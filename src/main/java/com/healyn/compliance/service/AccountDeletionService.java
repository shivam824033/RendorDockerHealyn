package com.healyn.compliance.service;

import com.healyn.audit.domain.AuditAction;
import com.healyn.audit.domain.AuditResource;
import com.healyn.audit.service.AuditLogger;
import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.service.AccountErasureService;
import com.healyn.auth.service.DeviceSessionService;
import com.healyn.auth.service.PasswordHasher;
import com.healyn.common.error.ConflictException;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.ForbiddenException;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnauthorizedException;
import com.healyn.common.id.UuidV7;
import com.healyn.compliance.config.ComplianceProperties;
import com.healyn.compliance.domain.AccountDeletionRequest;
import com.healyn.compliance.domain.DeletionRequestStatus;
import com.healyn.compliance.repository.AccountDeletionRequestRepository;
import com.healyn.notifications.service.FcmTokenService;
import com.healyn.patients.service.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// Orchestrates the account deletion / right-to-erasure flow. A request opens a cancellable
/// grace window; once it elapses, the scheduled sweep anonymizes the account's credentials and
/// contact details ({@code auth}), redacts patient identity PII ({@code patients}) and removes
/// device push tokens ({@code notifications}). Clinical records are retained, de-identified
/// (Hard Rule #7). Hard-purge of de-identified scaffolding is gated behind
/// {@code healyn.compliance.purge-enabled} and is a documented no-op by default.
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final AccountDeletionRequestRepository requests;
    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final DeviceSessionService sessions;
    private final AccountErasureService accountErasure;
    private final PatientService patients;
    private final FcmTokenService fcmTokens;
    private final AuditLogger audit;
    private final ComplianceProperties props;
    private final Clock clock;

    public AccountDeletionService(AccountDeletionRequestRepository requests, AccountRepository accounts,
                                  PasswordHasher passwordHasher, DeviceSessionService sessions,
                                  AccountErasureService accountErasure, PatientService patients,
                                  FcmTokenService fcmTokens, AuditLogger audit,
                                  ComplianceProperties props, Clock clock) {
        this.requests = requests;
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.sessions = sessions;
        this.accountErasure = accountErasure;
        this.patients = patients;
        this.fcmTokens = fcmTokens;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    /// Opens a deletion request after re-authenticating with the account password. Revokes all
    /// sessions so the device is signed out; the holder logs back in to cancel within the grace
    /// window. At most one active request per account (DB-enforced).
    @Transactional
    public AccountDeletionRequest request(UUID accountId, String password, String reason) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Account not found"));
        if (account.getRole() == AccountRole.ROLE_PHYSIO) {
            // The physio is the single operator/owner of the clinic app with no self-registration
            // path; anonymizing it would orphan all clinical data and brick the app. Erasure is a
            // patient (data-subject) right, not an operator action.
            throw new ForbiddenException(ErrorCode.COMPLIANCE_DELETION_NOT_ALLOWED,
                    "Account deletion is not available for this account");
        }
        if (!passwordHasher.matches(password, account.getPasswordHash(), account.getPasswordSalt())) {
            throw new UnauthorizedException(ErrorCode.COMPLIANCE_INVALID_PASSWORD, "Password does not match");
        }
        if (requests.findByAccountIdAndStatus(accountId, DeletionRequestStatus.REQUESTED).isPresent()) {
            throw new ConflictException(ErrorCode.COMPLIANCE_DELETION_ALREADY_REQUESTED,
                    "A deletion request is already in progress");
        }
        Instant now = Instant.now(clock);
        Instant purgeAfter = now.plus(Duration.ofDays(props.graceDays()));
        AccountDeletionRequest req = new AccountDeletionRequest(
                UuidV7.generate(), accountId, reason, now, purgeAfter);
        requests.save(req);
        account.markPendingDeletion();
        sessions.revokeAllForAccount(accountId);
        return req;
    }

    @Transactional
    public void cancel(UUID accountId) {
        AccountDeletionRequest req = requests.findByAccountIdAndStatus(accountId, DeletionRequestStatus.REQUESTED)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPLIANCE_DELETION_NOT_FOUND,
                        "No active deletion request"));
        req.cancel(Instant.now(clock));
        accounts.findById(accountId).ifPresent(Account::cancelPendingDeletion);
    }

    @Transactional(readOnly = true)
    public Optional<AccountDeletionRequest> activeRequest(UUID accountId) {
        return requests.findByAccountIdAndStatus(accountId, DeletionRequestStatus.REQUESTED);
    }

    /// Anonymizes every request whose grace window has elapsed. Idempotent per request: the
    /// status flips to ANONYMIZED so a later sweep skips it, and the underlying erasure
    /// methods no-op on already-erased data.
    @Transactional
    public int processDueAnonymizations() {
        Instant now = Instant.now(clock);
        List<AccountDeletionRequest> due = requests.findByStatusAndPurgeAfterLessThanEqual(
                DeletionRequestStatus.REQUESTED, now);
        for (AccountDeletionRequest req : due) {
            UUID accountId = req.getAccountId();
            accountErasure.anonymize(accountId, now);
            patients.anonymizeAccountPatients(accountId, now);
            fcmTokens.deleteForAccount(accountId);
            req.markAnonymized(now);
            audit.record(AuditAction.ANONYMIZE, accountId, null, AuditResource.ACCOUNT, accountId,
                    Map.of("deletion_request_id", req.getId().toString()));
            log.info("Anonymized account {} per deletion request {}", accountId, req.getId());
        }
        return due.size();
    }
}

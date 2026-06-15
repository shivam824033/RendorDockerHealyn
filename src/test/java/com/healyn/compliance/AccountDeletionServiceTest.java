package com.healyn.compliance;

import com.healyn.audit.domain.AuditAction;
import com.healyn.audit.service.AuditLogger;
import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.service.AccountErasureService;
import com.healyn.auth.service.DeviceSessionService;
import com.healyn.auth.service.PasswordHasher;
import com.healyn.common.error.ConflictException;
import com.healyn.common.error.ForbiddenException;
import com.healyn.common.error.UnauthorizedException;
import com.healyn.compliance.config.ComplianceProperties;
import com.healyn.compliance.domain.AccountDeletionRequest;
import com.healyn.compliance.domain.DeletionRequestStatus;
import com.healyn.compliance.repository.AccountDeletionRequestRepository;
import com.healyn.compliance.service.AccountDeletionService;
import com.healyn.notifications.service.FcmTokenService;
import com.healyn.patients.service.PatientService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-14T10:00:00Z");

    private final AccountDeletionRequestRepository requests = mock(AccountDeletionRequestRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final DeviceSessionService sessions = mock(DeviceSessionService.class);
    private final AccountErasureService accountErasure = mock(AccountErasureService.class);
    private final PatientService patients = mock(PatientService.class);
    private final FcmTokenService fcmTokens = mock(FcmTokenService.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final ComplianceProperties props =
            new ComplianceProperties(true, 60_000, 30, "en", false, 2920);
    private final AccountDeletionService service = new AccountDeletionService(
            requests, accounts, passwordHasher, sessions, accountErasure, patients, fcmTokens, audit,
            props, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void request_marks_pending_revokes_sessions_and_sets_grace_window() {
        UUID accountId = UUID.randomUUID();
        Account account = mock(Account.class);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordHasher.matches(any(), any(), any())).thenReturn(true);
        when(requests.findByAccountIdAndStatus(accountId, DeletionRequestStatus.REQUESTED))
                .thenReturn(Optional.empty());

        AccountDeletionRequest req = service.request(accountId, "correct horse", "leaving");

        assertThat(req.getPurgeAfter()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        verify(requests).save(any(AccountDeletionRequest.class));
        verify(account).markPendingDeletion();
        verify(sessions).revokeAllForAccount(accountId);
    }

    @Test
    void request_by_physio_owner_is_forbidden_and_changes_nothing() {
        UUID accountId = UUID.randomUUID();
        Account account = mock(Account.class);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(account.getRole()).thenReturn(AccountRole.ROLE_PHYSIO);

        assertThatThrownBy(() -> service.request(accountId, "correct horse", "leaving"))
                .isInstanceOf(ForbiddenException.class);

        verify(passwordHasher, never()).matches(any(), any(), any());
        verify(requests, never()).save(any());
        verify(sessions, never()).revokeAllForAccount(any());
    }

    @Test
    void request_with_wrong_password_is_rejected_and_changes_nothing() {
        UUID accountId = UUID.randomUUID();
        Account account = mock(Account.class);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordHasher.matches(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.request(accountId, "wrong", null))
                .isInstanceOf(UnauthorizedException.class);

        verify(requests, never()).save(any());
        verify(sessions, never()).revokeAllForAccount(any());
    }

    @Test
    void request_when_one_already_active_conflicts() {
        UUID accountId = UUID.randomUUID();
        Account account = mock(Account.class);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordHasher.matches(any(), any(), any())).thenReturn(true);
        when(requests.findByAccountIdAndStatus(accountId, DeletionRequestStatus.REQUESTED))
                .thenReturn(Optional.of(mock(AccountDeletionRequest.class)));

        assertThatThrownBy(() -> service.request(accountId, "correct", null))
                .isInstanceOf(ConflictException.class);
        verify(requests, never()).save(any());
    }

    @Test
    void cancel_restores_account_and_marks_request_cancelled() {
        UUID accountId = UUID.randomUUID();
        AccountDeletionRequest req = new AccountDeletionRequest(
                UUID.randomUUID(), accountId, null, NOW, NOW.plus(Duration.ofDays(30)));
        when(requests.findByAccountIdAndStatus(accountId, DeletionRequestStatus.REQUESTED))
                .thenReturn(Optional.of(req));
        Account account = mock(Account.class);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        service.cancel(accountId);

        assertThat(req.getStatus()).isEqualTo(DeletionRequestStatus.CANCELLED);
        verify(account).cancelPendingDeletion();
    }

    @Test
    void due_requests_are_anonymized_across_modules_and_marked() {
        UUID accountId = UUID.randomUUID();
        AccountDeletionRequest due = new AccountDeletionRequest(
                UUID.randomUUID(), accountId, null, NOW.minus(Duration.ofDays(31)), NOW.minus(Duration.ofDays(1)));
        when(requests.findByStatusAndPurgeAfterLessThanEqual(eq(DeletionRequestStatus.REQUESTED), any()))
                .thenReturn(List.of(due));

        int processed = service.processDueAnonymizations();

        assertThat(processed).isEqualTo(1);
        verify(accountErasure).anonymize(accountId, NOW);
        verify(patients).anonymizeAccountPatients(accountId, NOW);
        verify(fcmTokens).deleteForAccount(accountId);
        verify(audit).record(eq(AuditAction.ANONYMIZE), eq(accountId), any(), any(), eq(accountId), any());
        assertThat(due.getStatus()).isEqualTo(DeletionRequestStatus.ANONYMIZED);
        assertThat(due.getAnonymizedAt()).isEqualTo(NOW);
    }
}

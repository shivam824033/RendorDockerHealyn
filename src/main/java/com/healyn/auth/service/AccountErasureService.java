package com.healyn.auth.service;

import com.healyn.auth.domain.Account;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.repository.DeviceSessionRepository;
import com.healyn.auth.repository.OtpChallengeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/// Erases the {@code auth}-owned personal data of an account: it nulls the account's
/// credentials and contact details, overwrites the password hash with an unusable random
/// value, disables the account, and drops the device-session and OTP rows that carry device
/// labels, IPs, user agents and contact targets. Called by the compliance module's deletion
/// orchestrator at the end of the grace window. Idempotent — an already-anonymized account
/// is left untouched.
@Service
public class AccountErasureService {

    private final AccountRepository accounts;
    private final OtpChallengeRepository otpChallenges;
    private final DeviceSessionRepository deviceSessions;
    private final PasswordHasher passwordHasher;
    private final SecureRandom random = new SecureRandom();

    public AccountErasureService(AccountRepository accounts,
                                 OtpChallengeRepository otpChallenges,
                                 DeviceSessionRepository deviceSessions,
                                 PasswordHasher passwordHasher) {
        this.accounts = accounts;
        this.otpChallenges = otpChallenges;
        this.deviceSessions = deviceSessions;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public void anonymize(UUID accountId, Instant when) {
        Account account = accounts.findById(accountId).orElse(null);
        if (account == null || account.getDeletedAt() != null) {
            return; // already erased or never existed — nothing to do
        }
        PasswordHasher.Hashed unusable = passwordHasher.hash(randomSecret());
        account.anonymize(unusable.hash(), unusable.salt(), when);
        otpChallenges.deleteByAccountId(accountId);
        deviceSessions.deleteByAccountId(accountId);
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}

package com.healyn.auth.service;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountStatus;
import com.healyn.auth.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class LoginService {

    private static final int MAX_FAILED = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final DeviceSessionService sessions;

    public LoginService(AccountRepository accounts, PasswordHasher passwordHasher, DeviceSessionService sessions) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.sessions = sessions;
    }

    @Transactional
    public Result authenticate(String emailOrPhone, String rawPassword, DeviceMeta device) {
        Account account = lookup(emailOrPhone).orElse(null);
        Instant now = Instant.now();

        if (account == null || account.getDeletedAt() != null || account.getStatus() == AccountStatus.DISABLED) {
            // Burn the same Argon2 cost as the wrong-password path so a missing/disabled account
            // is not distinguishable by response time (audit M1 — login enumeration oracle).
            passwordHasher.matchesDecoy(rawPassword);
            return Result.invalid();
        }

        if (account.getLockedUntil() != null && now.isBefore(account.getLockedUntil())) {
            return Result.locked();
        }
        if (account.getStatus() == AccountStatus.LOCKED) {
            account.unlock();
        }

        if (!passwordHasher.matches(rawPassword, account.getPasswordHash(), account.getPasswordSalt())) {
            account.recordFailedLogin();
            if (account.getFailedLoginCount() >= MAX_FAILED) {
                account.lock(now.plus(LOCKOUT));
            }
            return Result.invalid();
        }

        account.recordSuccessfulLogin(now);
        return Result.success(sessions.issue(account, device));
    }

    private Optional<Account> lookup(String identifier) {
        if (identifier == null) return Optional.empty();
        return identifier.contains("@")
                ? accounts.findByEmail(identifier.toLowerCase())
                : accounts.findByPhoneE164(identifier);
    }

    public sealed interface Result {
        static Result success(IssuedSession s) { return new Success(s); }
        static Result invalid() { return Invalid.INSTANCE; }
        static Result locked() { return Locked.INSTANCE; }

        record Success(IssuedSession session) implements Result {}
        enum Invalid implements Result { INSTANCE }
        enum Locked implements Result { INSTANCE }
    }
}

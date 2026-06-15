package com.healyn.auth.service;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.domain.AccountStatus;
import com.healyn.auth.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginServiceTest {

    private AccountRepository accounts;
    private PasswordHasher passwordHasher;
    private DeviceSessionService sessions;
    private LoginService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        sessions = mock(DeviceSessionService.class);
        service = new LoginService(accounts, passwordHasher, sessions);
    }

    @Test
    void missing_account_runs_decoy_hash_and_returns_invalid() {
        when(accounts.findByEmail(anyString())).thenReturn(Optional.empty());

        LoginService.Result result = service.authenticate("nobody@example.com", "some-password", device());

        assertThat(result).isInstanceOf(LoginService.Result.Invalid.class);
        // M1: a missing account must still pay the Argon2 cost so it is not distinguishable by timing.
        verify(passwordHasher).matchesDecoy("some-password");
        // ...and the real comparison is never reached (no hash to compare against).
        verify(passwordHasher, never()).matches(anyString(), any(), any());
    }

    @Test
    void wrong_password_returns_invalid_without_decoy() {
        Account account = new Account(UUID.randomUUID(), "user@example.com", null,
                "hash", new byte[]{1, 2, 3}, AccountRole.ROLE_ACCOUNT);
        when(accounts.findByEmail("user@example.com")).thenReturn(Optional.of(account));
        when(passwordHasher.matches(anyString(), any(), any())).thenReturn(false);

        LoginService.Result result = service.authenticate("user@example.com", "wrong", device());

        assertThat(result).isInstanceOf(LoginService.Result.Invalid.class);
        verify(passwordHasher, never()).matchesDecoy(anyString());
    }

    @Test
    void disabled_account_runs_decoy_and_returns_invalid() {
        Account account = new Account(UUID.randomUUID(), "user@example.com", null,
                "hash", new byte[]{1}, AccountRole.ROLE_ACCOUNT);
        account.anonymize("unusable", new byte[]{9}, java.time.Instant.now());
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DISABLED);
        when(accounts.findByEmail(anyString())).thenReturn(Optional.of(account));

        LoginService.Result result = service.authenticate("user@example.com", "pw", device());

        assertThat(result).isInstanceOf(LoginService.Result.Invalid.class);
        verify(passwordHasher).matchesDecoy("pw");
    }

    private static DeviceMeta device() {
        return new DeviceMeta("dev-1", "Test", null, "127.0.0.1", "JUnit");
    }
}

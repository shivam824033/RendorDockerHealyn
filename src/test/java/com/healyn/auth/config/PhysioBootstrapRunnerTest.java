package com.healyn.auth.config;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.service.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhysioBootstrapRunnerTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);

    @Test
    void provisions_a_physio_account_when_enabled_and_absent() {
        when(accounts.existsByEmail("owner@clinic.example")).thenReturn(false);
        when(hasher.hash("Temp!Pass123"))
                .thenReturn(new PasswordHasher.Hashed("hash", new byte[] {1, 2}));

        new PhysioBootstrapRunner(accounts, hasher, true, "owner@clinic.example", "Temp!Pass123")
                .run(null);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(captor.capture());
        Account saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(AccountRole.ROLE_PHYSIO);
        assertThat(saved.getEmail()).isEqualTo("owner@clinic.example");
        assertThat(saved.getPhoneE164()).isNull();
    }

    @Test
    void does_nothing_when_disabled() {
        new PhysioBootstrapRunner(accounts, hasher, false, "owner@clinic.example", "Temp!Pass123")
                .run(null);

        verifyNoInteractions(accounts, hasher);
    }

    @Test
    void is_idempotent_and_normalises_email_case() {
        when(accounts.existsByEmail("owner@clinic.example")).thenReturn(true);

        new PhysioBootstrapRunner(accounts, hasher, true, "Owner@Clinic.Example", "Temp!Pass123")
                .run(null);

        verify(accounts, never()).save(any());
        verifyNoInteractions(hasher);
    }

    @Test
    void skips_when_enabled_but_credentials_are_blank() {
        new PhysioBootstrapRunner(accounts, hasher, true, "  ", "Temp!Pass123").run(null);

        verify(accounts, never()).save(any());
        verifyNoInteractions(hasher);
    }
}

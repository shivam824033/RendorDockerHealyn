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

class DevPhysioSeederTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);

    @Test
    void seeds_a_physio_account_when_absent() {
        when(accounts.existsByEmail("physio@healyn.local")).thenReturn(false);
        when(hasher.hash("Physio!Dev123"))
                .thenReturn(new PasswordHasher.Hashed("hash", new byte[] {1, 2}));

        new DevPhysioSeeder(accounts, hasher, "physio@healyn.local", "Physio!Dev123").run(null);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(captor.capture());
        Account saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(AccountRole.ROLE_PHYSIO);
        assertThat(saved.getEmail()).isEqualTo("physio@healyn.local");
        assertThat(saved.getPhoneE164()).isNull();
    }

    @Test
    void is_idempotent_and_normalises_email_case() {
        when(accounts.existsByEmail("physio@healyn.local")).thenReturn(true);

        new DevPhysioSeeder(accounts, hasher, "Physio@Healyn.Local", "Physio!Dev123").run(null);

        verify(accounts, never()).save(any());
        verifyNoInteractions(hasher);
    }

    @Test
    void skips_when_credentials_are_blank() {
        new DevPhysioSeeder(accounts, hasher, "  ", "Physio!Dev123").run(null);

        verify(accounts, never()).save(any());
        verifyNoInteractions(hasher);
    }
}

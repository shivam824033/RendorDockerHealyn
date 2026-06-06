package com.healyn.notifications;

import com.healyn.notifications.domain.FcmToken;
import com.healyn.notifications.repository.FcmTokenRepository;
import com.healyn.notifications.service.FcmTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FcmTokenServiceTest {

    private final FcmTokenRepository repo = mock(FcmTokenRepository.class);
    private final FcmTokenService service = new FcmTokenService(repo);

    @Test
    void register_new_token_inserts_a_row() {
        UUID account = UUID.randomUUID();
        when(repo.findByTokenAndDeletedAtIsNull("tok-1")).thenReturn(Optional.empty());

        service.register(account, "tok-1", "android", "dev-1");

        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        verify(repo).save(captor.capture());
        FcmToken saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(account);
        assertThat(saved.getToken()).isEqualTo("tok-1");
        assertThat(saved.getPlatform()).isEqualTo("android");
        assertThat(saved.getDeviceId()).isEqualTo("dev-1");
    }

    @Test
    void register_defaults_platform_to_android_when_blank() {
        when(repo.findByTokenAndDeletedAtIsNull("tok-2")).thenReturn(Optional.empty());

        service.register(UUID.randomUUID(), "tok-2", "  ", null);

        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getPlatform()).isEqualTo("android");
    }

    @Test
    void register_existing_token_reassigns_without_inserting() {
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        FcmToken existing = new FcmToken(UUID.randomUUID(), firstOwner, "tok-3", "android", "dev-a");
        when(repo.findByTokenAndDeletedAtIsNull("tok-3")).thenReturn(Optional.of(existing));

        UUID returnedId = service.register(secondOwner, "tok-3", "android", "dev-b");

        assertThat(returnedId).isEqualTo(existing.getId());
        assertThat(existing.getAccountId()).isEqualTo(secondOwner);
        assertThat(existing.getDeviceId()).isEqualTo("dev-b");
        verify(repo, never()).save(any(FcmToken.class));
    }
}

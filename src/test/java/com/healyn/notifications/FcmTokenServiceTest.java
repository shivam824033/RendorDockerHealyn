package com.healyn.notifications;

import com.healyn.notifications.domain.FcmToken;
import com.healyn.notifications.repository.FcmTokenRepository;
import com.healyn.notifications.service.FcmTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
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

    @Test
    void register_supersedes_an_older_live_token_for_the_same_device() {
        UUID account = UUID.randomUUID();
        when(repo.findByTokenAndDeletedAtIsNull("tok-new")).thenReturn(Optional.empty());
        FcmToken older = new FcmToken(UUID.randomUUID(), account, "tok-old", "android", "dev-1");
        when(repo.findByAccountIdAndDeviceIdAndDeletedAtIsNull(account, "dev-1"))
                .thenReturn(List.of(older));

        service.register(account, "tok-new", "android", "dev-1");

        assertThat(older.getDeletedAt()).as("the rotated-away token is retired").isNotNull();
    }

    @Test
    void unregister_retires_every_live_token_for_the_account_and_device() {
        UUID account = UUID.randomUUID();
        FcmToken t1 = new FcmToken(UUID.randomUUID(), account, "tok-a", "android", "dev-1");
        FcmToken t2 = new FcmToken(UUID.randomUUID(), account, "tok-b", "android", "dev-1");
        when(repo.findByAccountIdAndDeviceIdAndDeletedAtIsNull(account, "dev-1"))
                .thenReturn(List.of(t1, t2));

        int retired = service.unregister(account, "dev-1");

        assertThat(retired).isEqualTo(2);
        assertThat(t1.getDeletedAt()).isNotNull();
        assertThat(t2.getDeletedAt()).isNotNull();
    }

    @Test
    void unregister_with_blank_device_id_retires_nothing() {
        int retired = service.unregister(UUID.randomUUID(), "  ");

        assertThat(retired).isZero();
        verify(repo, never()).findByAccountIdAndDeviceIdAndDeletedAtIsNull(any(), any());
    }
}

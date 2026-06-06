package com.healyn.notifications;

import com.healyn.notifications.adapter.LoggingFcmSender;
import com.healyn.notifications.config.FcmProperties;
import com.healyn.notifications.config.NotificationsConfig;
import com.healyn.notifications.port.FcmSenderPort;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the FCM sender selection: blank credentials fall back to the logging sender only in
 * the ephemeral local/test profiles, and fail fast everywhere else so a misconfigured deploy
 * can't silently stop delivering push.
 */
class NotificationsConfigTest {

    private final NotificationsConfig config = new NotificationsConfig();

    @Test
    void blank_credentials_uses_logging_sender_in_local() throws Exception {
        FcmSenderPort sender = config.fcmSenderPort(new FcmProperties(""), env("local"));
        assertThat(sender).isInstanceOf(LoggingFcmSender.class);
    }

    @Test
    void null_credentials_uses_logging_sender_in_test() throws Exception {
        FcmSenderPort sender = config.fcmSenderPort(new FcmProperties(null), env("test"));
        assertThat(sender).isInstanceOf(LoggingFcmSender.class);
    }

    @Test
    void blank_credentials_fails_fast_outside_local_and_test() {
        assertThatThrownBy(() -> config.fcmSenderPort(new FcmProperties(""), env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credentials-path");
    }

    @Test
    void blank_credentials_fails_fast_with_no_active_profile() {
        assertThatThrownBy(() -> config.fcmSenderPort(new FcmProperties(""), env()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static MockEnvironment env(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }
}

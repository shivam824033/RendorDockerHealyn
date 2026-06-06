package com.healyn.notifications.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.healyn.notifications.adapter.FirebaseFcmSender;
import com.healyn.notifications.adapter.LoggingFcmSender;
import com.healyn.notifications.port.FcmSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({NotificationProperties.class, FcmProperties.class})
public class NotificationsConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationsConfig.class);

    /** Profiles where falling back to the logging sender (no real push) is acceptable. */
    private static final Set<String> EPHEMERAL_PROFILES = Set.of("local", "test");

    /**
     * Real FCM delivery when {@code healyn.fcm.credentials-path} points at a service-account
     * file; otherwise the logging sender (no push leaves the system). A blank value counts as
     * "not configured" — the dev {@code .env} carries {@code HEALYN_FCM_CREDENTIALS_PATH=} to
     * mean exactly that, mirroring how {@code JwtKeyProvider} treats blank key paths.
     *
     * <p>Outside the {@code local}/{@code test} profiles this fails fast rather than open: a
     * deploy with no credentials would silently stop delivering push, so we refuse to start
     * instead. This mirrors {@link com.healyn.auth.service.JwtKeyProvider}'s key-path guard.
     */
    @Bean
    @ConditionalOnMissingBean(FcmSenderPort.class)
    public FcmSenderPort fcmSenderPort(FcmProperties props, Environment env) throws IOException {
        String credentialsPath = props.credentialsPath();
        if (credentialsPath == null || credentialsPath.isBlank()) {
            boolean ephemeralOk = false;
            for (String p : env.getActiveProfiles()) if (EPHEMERAL_PROFILES.contains(p)) ephemeralOk = true;
            if (!ephemeralOk) {
                throw new IllegalStateException(
                        "healyn.fcm.credentials-path must be configured outside the local/test profiles; "
                                + "refusing to start with the logging sender, which would silently drop all push.");
            }
            log.info("FCM credentials-path not configured — using logging sender (no push delivery).");
            return new LoggingFcmSender();
        }
        try (InputStream credentials = Files.newInputStream(Path.of(credentialsPath))) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            log.info("FCM credentials loaded from {} — using firebase-admin sender.", credentialsPath);
            return new FirebaseFcmSender(FirebaseMessaging.getInstance(app));
        }
    }
}

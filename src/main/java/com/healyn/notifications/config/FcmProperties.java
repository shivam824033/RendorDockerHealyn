package com.healyn.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Cloud Messaging credentials. Bound from {@code healyn.fcm.*}
 * ({@code HEALYN_FCM_CREDENTIALS_PATH}). When {@code credentialsPath} is absent the
 * real adapter is not created and the logging sender is used instead.
 */
@ConfigurationProperties(prefix = "healyn.fcm")
public record FcmProperties(String credentialsPath) {}

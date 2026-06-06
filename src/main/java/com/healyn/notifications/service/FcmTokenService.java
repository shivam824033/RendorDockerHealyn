package com.healyn.notifications.service;

import com.healyn.common.id.UuidV7;
import com.healyn.notifications.domain.FcmToken;
import com.healyn.notifications.repository.FcmTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Registers and resolves device FCM tokens. Registration is an idempotent upsert keyed on
 * the token: re-registering a known token rebinds it to the caller's account (device re-login)
 * and refreshes its metadata, so a token is never owned by two accounts at once.
 */
@Service
public class FcmTokenService {

    private static final String DEFAULT_PLATFORM = "android";

    private final FcmTokenRepository tokens;

    public FcmTokenService(FcmTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Transactional
    public UUID register(UUID accountId, String token, String platform, String deviceId) {
        String resolvedPlatform = (platform == null || platform.isBlank()) ? DEFAULT_PLATFORM : platform;
        return tokens.findByTokenAndDeletedAtIsNull(token)
                .map(existing -> {
                    existing.reassignTo(accountId, resolvedPlatform, deviceId);
                    return existing.getId();
                })
                .orElseGet(() -> {
                    FcmToken created = new FcmToken(UuidV7.generate(), accountId, token, resolvedPlatform, deviceId);
                    tokens.save(created);
                    return created.getId();
                });
    }
}

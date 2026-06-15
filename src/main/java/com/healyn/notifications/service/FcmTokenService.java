package com.healyn.notifications.service;

import com.healyn.common.id.UuidV7;
import com.healyn.notifications.domain.FcmToken;
import com.healyn.notifications.repository.FcmTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Registers and resolves device FCM tokens. Registration is an idempotent upsert keyed on
 * the token: re-registering a known token rebinds it to the caller's account (device re-login)
 * and refreshes its metadata, so a token is never owned by two accounts at once. Registration
 * also supersedes any older live token for the same device, so a device maps to exactly one
 * live token even after the OS rotates it. Logout retires the device's token via
 * {@link #unregister(UUID, String)} so a signed-out device stops resolving as a delivery target.
 */
@Service
public class FcmTokenService {

    private static final String DEFAULT_PLATFORM = "android";

    private final FcmTokenRepository tokens;

    public FcmTokenService(FcmTokenRepository tokens) {
        this.tokens = tokens;
    }

    /// Erasure: removes every device token for an account (right-to-erasure / anonymization).
    @Transactional
    public int deleteForAccount(UUID accountId) {
        return tokens.deleteByAccountId(accountId);
    }

    @Transactional
    public UUID register(UUID accountId, String token, String platform, String deviceId) {
        String resolvedPlatform = (platform == null || platform.isBlank()) ? DEFAULT_PLATFORM : platform;
        UUID keptId = tokens.findByTokenAndDeletedAtIsNull(token)
                .map(existing -> {
                    existing.reassignTo(accountId, resolvedPlatform, deviceId);
                    return existing.getId();
                })
                .orElseGet(() -> {
                    FcmToken created = new FcmToken(UuidV7.generate(), accountId, token, resolvedPlatform, deviceId);
                    tokens.save(created);
                    return created.getId();
                });
        // Retire any older live token still bound to this account+device (e.g. the OS rotated
        // the FCM token and the prior row was never retired), so a device resolves to one token.
        retireDeviceTokens(accountId, deviceId, keptId);
        return keptId;
    }

    /**
     * Logout / device sign-out: retires every live token bound to this account on this device, so
     * the dispatcher stops resolving it as a delivery target. Scoped to the caller's account, so a
     * device shared by two accounts only loses the signing-out account's token; the account's other
     * devices keep their own tokens. Returns the number of tokens retired.
     */
    @Transactional
    public int unregister(UUID accountId, String deviceId) {
        return retireDeviceTokens(accountId, deviceId, null);
    }

    /// Retires this account+device's live tokens, skipping {@code keepId} (the one just registered).
    /// A blank device id can't be matched to one device, so nothing is retired in that case.
    private int retireDeviceTokens(UUID accountId, String deviceId, UUID keepId) {
        if (deviceId == null || deviceId.isBlank()) return 0;
        List<FcmToken> live = tokens.findByAccountIdAndDeviceIdAndDeletedAtIsNull(accountId, deviceId);
        int retired = 0;
        for (FcmToken t : live) {
            if (t.getId().equals(keepId)) continue;
            t.retire();
            retired++;
        }
        return retired;
    }
}

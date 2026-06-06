package com.healyn.notifications.port;

import com.healyn.notifications.domain.NotificationKind;

import java.util.Map;

/**
 * The outbound push edge. Implemented by a logging adapter in Phase 1; the real
 * {@code firebase-admin}-backed adapter is a drop-in once that dependency is approved
 * (DEVELOPMENT_RULES §9). The {@code data} map carries IDs only — never PHI (Hard Rule #4).
 */
public interface FcmSenderPort {

    FcmSendOutcome send(String token, NotificationKind kind, Map<String, String> data);
}

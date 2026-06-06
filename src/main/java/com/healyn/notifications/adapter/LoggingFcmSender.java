package com.healyn.notifications.adapter;

import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.port.FcmSendOutcome;
import com.healyn.notifications.port.FcmSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Phase 1 default sender: logs the dispatch and reports success without contacting FCM.
 * Lets the full outbox pipeline (poll → resolve tokens → mark SENT) run end-to-end before
 * the real {@code firebase-admin} adapter is wired. Logs a token fingerprint, never the raw
 * token, and payload keys only — never PHI (Hard Rules #3/#4).
 */
public class LoggingFcmSender implements FcmSenderPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingFcmSender.class);

    @Override
    public FcmSendOutcome send(String token, NotificationKind kind, Map<String, String> data) {
        log.info("FCM dispatch (logging adapter) kind={} tokenFp={} payloadKeys={}",
                kind, fingerprint(token), data.keySet());
        return FcmSendOutcome.DELIVERED;
    }

    private static String fingerprint(String token) {
        return token == null ? "null" : Integer.toHexString(token.hashCode());
    }
}

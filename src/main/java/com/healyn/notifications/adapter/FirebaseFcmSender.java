package com.healyn.notifications.adapter;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.port.FcmSendOutcome;
import com.healyn.notifications.port.FcmSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Real FCM delivery via the Firebase Admin SDK. Sends a <em>data-only</em> message: the
 * payload carries IDs only and never a notification title/body, so no PHI leaves the system
 * (Hard Rule #4). The client builds the user-facing notification from the IDs.
 *
 * <p>FCM error codes are mapped so the dispatcher can react: unregistered/invalid tokens are
 * retired, transient failures are retried with backoff.
 */
public class FirebaseFcmSender implements FcmSenderPort {

    private static final Logger log = LoggerFactory.getLogger(FirebaseFcmSender.class);

    private final FirebaseMessaging messaging;

    public FirebaseFcmSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public FcmSendOutcome send(String token, NotificationKind kind, Map<String, String> data) {
        Message message = Message.builder()
                .setToken(token)
                .putData("kind", kind.name())
                .putAllData(data)
                .build();
        try {
            messaging.send(message);
            return FcmSendOutcome.DELIVERED;
        } catch (FirebaseMessagingException e) {
            FcmSendOutcome outcome = classify(e.getMessagingErrorCode());
            if (outcome == FcmSendOutcome.TRANSIENT_ERROR) {
                log.warn("FCM transient failure kind={} errorCode={}", kind, e.getMessagingErrorCode());
            }
            return outcome;
        }
    }

    /** Map an FCM messaging error to a dispatch outcome. */
    public static FcmSendOutcome classify(MessagingErrorCode code) {
        if (code == null) {
            return FcmSendOutcome.TRANSIENT_ERROR;
        }
        return switch (code) {
            case UNREGISTERED, INVALID_ARGUMENT, SENDER_ID_MISMATCH -> FcmSendOutcome.TOKEN_INVALID;
            case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED, THIRD_PARTY_AUTH_ERROR -> FcmSendOutcome.TRANSIENT_ERROR;
        };
    }
}

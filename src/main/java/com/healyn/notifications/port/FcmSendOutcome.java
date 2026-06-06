package com.healyn.notifications.port;

/** The result of attempting to deliver one notification to one device token. */
public enum FcmSendOutcome {

    /** Accepted by FCM for delivery. */
    DELIVERED,

    /** FCM reports the token as unregistered / invalid; it should be retired. */
    TOKEN_INVALID,

    /** A transient failure (network, 5xx, throttling); the row should be retried. */
    TRANSIENT_ERROR
}

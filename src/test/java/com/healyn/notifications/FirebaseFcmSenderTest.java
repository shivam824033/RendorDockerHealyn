package com.healyn.notifications;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.healyn.notifications.adapter.FirebaseFcmSender;
import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.port.FcmSendOutcome;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirebaseFcmSenderTest {

    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final FirebaseFcmSender sender = new FirebaseFcmSender(messaging);

    @Test
    void successful_send_is_delivered() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/p/messages/1");

        FcmSendOutcome outcome = sender.send("tok-1", NotificationKind.BOOKING_CONFIRMED,
                Map.of("appointmentId", "a1"));

        assertThat(outcome).isEqualTo(FcmSendOutcome.DELIVERED);
    }

    @Test
    void unregistered_token_maps_to_token_invalid() throws Exception {
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(ex);

        assertThat(sender.send("tok-dead", NotificationKind.DISCUSSION_NEW_MESSAGE, Map.of("x", "y")))
                .isEqualTo(FcmSendOutcome.TOKEN_INVALID);
    }

    @Test
    void server_unavailable_maps_to_transient_error() throws Exception {
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(messaging.send(any(Message.class))).thenThrow(ex);

        assertThat(sender.send("tok-1", NotificationKind.BOOKING_REQUESTED, Map.of("x", "y")))
                .isEqualTo(FcmSendOutcome.TRANSIENT_ERROR);
    }

    @Test
    void classify_covers_each_error_code() {
        assertThat(FirebaseFcmSender.classify(MessagingErrorCode.UNREGISTERED)).isEqualTo(FcmSendOutcome.TOKEN_INVALID);
        assertThat(FirebaseFcmSender.classify(MessagingErrorCode.INVALID_ARGUMENT)).isEqualTo(FcmSendOutcome.TOKEN_INVALID);
        assertThat(FirebaseFcmSender.classify(MessagingErrorCode.SENDER_ID_MISMATCH)).isEqualTo(FcmSendOutcome.TOKEN_INVALID);
        assertThat(FirebaseFcmSender.classify(MessagingErrorCode.INTERNAL)).isEqualTo(FcmSendOutcome.TRANSIENT_ERROR);
        assertThat(FirebaseFcmSender.classify(MessagingErrorCode.QUOTA_EXCEEDED)).isEqualTo(FcmSendOutcome.TRANSIENT_ERROR);
        assertThat(FirebaseFcmSender.classify(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR)).isEqualTo(FcmSendOutcome.TRANSIENT_ERROR);
        assertThat(FirebaseFcmSender.classify(MessagingErrorCode.UNAVAILABLE)).isEqualTo(FcmSendOutcome.TRANSIENT_ERROR);
        assertThat(FirebaseFcmSender.classify(null)).isEqualTo(FcmSendOutcome.TRANSIENT_ERROR);
    }
}

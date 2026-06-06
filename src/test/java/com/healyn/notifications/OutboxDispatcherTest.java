package com.healyn.notifications;

import com.healyn.notifications.config.NotificationProperties;
import com.healyn.notifications.domain.FcmToken;
import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.domain.NotificationOutbox;
import com.healyn.notifications.domain.NotificationStatus;
import com.healyn.notifications.port.FcmSendOutcome;
import com.healyn.notifications.port.FcmSenderPort;
import com.healyn.notifications.repository.FcmTokenRepository;
import com.healyn.notifications.repository.NotificationOutboxRepository;
import com.healyn.notifications.service.OutboxDispatcher;
import com.healyn.notifications.service.OutboxTransactions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-06-03T09:00:00Z");

    private final NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
    private final FcmTokenRepository tokens = mock(FcmTokenRepository.class);
    private final AtomicReference<FcmSendOutcome> outcome = new AtomicReference<>(FcmSendOutcome.DELIVERED);
    private final FcmSenderPort sender = (token, kind, data) -> outcome.get();

    private OutboxDispatcher dispatcher(int maxAttempts) {
        return dispatcher(maxAttempts, sender);
    }

    private OutboxDispatcher dispatcher(int maxAttempts, FcmSenderPort sender) {
        NotificationProperties props = new NotificationProperties(true, 2000L, 50, maxAttempts, 2L, 60L);
        OutboxTransactions transactions = new OutboxTransactions(outbox, tokens, props);
        return new OutboxDispatcher(transactions, tokens, sender, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private NotificationOutbox dueRow(UUID account) {
        NotificationOutbox row = new NotificationOutbox(
                UUID.randomUUID(), NotificationKind.BOOKING_CONFIRMED, account,
                Map.of("appointmentId", UUID.randomUUID().toString()), UUID.randomUUID(), NOW);
        when(outbox.findDueForDispatch(eq(NotificationStatus.PENDING), eq(NOW), any()))
                .thenReturn(List.of(row));
        when(outbox.findById(row.getId())).thenReturn(Optional.of(row));
        return row;
    }

    @Test
    void delivered_marks_row_sent_with_resolved_token() {
        UUID account = UUID.randomUUID();
        NotificationOutbox row = dueRow(account);
        FcmToken token = new FcmToken(UUID.randomUUID(), account, "tok-1", "android", "dev-1");
        when(tokens.findByAccountIdAndDeletedAtIsNull(account)).thenReturn(List.of(token));
        outcome.set(FcmSendOutcome.DELIVERED);

        assertThat(dispatcher(5).dispatchDue()).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.getSentAt()).isEqualTo(NOW);
        assertThat(row.getTargetFcmToken()).isEqualTo("tok-1");
        assertThat(row.getAttempts()).isEqualTo((short) 1);
    }

    @Test
    void no_live_tokens_marks_row_sent_terminally() {
        UUID account = UUID.randomUUID();
        NotificationOutbox row = dueRow(account);
        when(tokens.findByAccountIdAndDeletedAtIsNull(account)).thenReturn(List.of());

        dispatcher(5).dispatchDue();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.getTargetFcmToken()).isNull();
    }

    @Test
    void invalid_token_is_retired_and_row_terminal_when_none_deliverable() {
        UUID account = UUID.randomUUID();
        NotificationOutbox row = dueRow(account);
        FcmToken token = new FcmToken(UUID.randomUUID(), account, "tok-dead", "android", "dev-1");
        when(tokens.findByAccountIdAndDeletedAtIsNull(account)).thenReturn(List.of(token));
        when(tokens.findByTokenAndDeletedAtIsNull("tok-dead")).thenReturn(Optional.of(token));
        outcome.set(FcmSendOutcome.TOKEN_INVALID);

        dispatcher(5).dispatchDue();
        assertThat(token.getDeletedAt()).isNotNull();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void sender_exception_is_isolated_and_row_rescheduled() {
        UUID account = UUID.randomUUID();
        NotificationOutbox row = dueRow(account);
        FcmToken token = new FcmToken(UUID.randomUUID(), account, "tok-1", "android", "dev-1");
        when(tokens.findByAccountIdAndDeletedAtIsNull(account)).thenReturn(List.of(token));
        FcmSenderPort throwing = (t, kind, data) -> { throw new RuntimeException("boom"); };

        // A throwing send must not abort the sweep or wedge the queue: the row is rescheduled.
        int processed = dispatcher(5, throwing).dispatchDue();

        assertThat(processed).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo((short) 1);
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(row.getLastError()).isEqualTo("transient_fcm_error");
    }

    @Test
    void transient_failure_reschedules_with_backoff_when_retries_remain() {
        UUID account = UUID.randomUUID();
        NotificationOutbox row = dueRow(account);
        FcmToken token = new FcmToken(UUID.randomUUID(), account, "tok-1", "android", "dev-1");
        when(tokens.findByAccountIdAndDeletedAtIsNull(account)).thenReturn(List.of(token));
        outcome.set(FcmSendOutcome.TRANSIENT_ERROR);

        dispatcher(5).dispatchDue();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(row.getAttempts()).isEqualTo((short) 1);
        // base 2s * 2^(attempt-1) = 2s for the first retry
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(row.getLastError()).isEqualTo("transient_fcm_error");
    }

    @Test
    void transient_failure_marks_dead_when_attempts_exhausted() {
        UUID account = UUID.randomUUID();
        NotificationOutbox row = dueRow(account);
        FcmToken token = new FcmToken(UUID.randomUUID(), account, "tok-1", "android", "dev-1");
        when(tokens.findByAccountIdAndDeletedAtIsNull(account)).thenReturn(List.of(token));
        outcome.set(FcmSendOutcome.TRANSIENT_ERROR);

        dispatcher(1).dispatchDue();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.DEAD);
        assertThat(row.getAttempts()).isEqualTo((short) 1);
    }
}

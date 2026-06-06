package com.healyn.notifications;

import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.domain.NotificationOutbox;
import com.healyn.notifications.domain.NotificationStatus;
import com.healyn.notifications.repository.NotificationOutboxRepository;
import com.healyn.notifications.service.NotificationPreferencesService;
import com.healyn.notifications.service.NotificationPublisher;
import com.healyn.patients.repository.AccountPatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPublisherTest {

    private static final Instant NOW = Instant.parse("2026-06-02T09:00:00Z");

    private final NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
    private final AccountPatientRepository accountPatients = mock(AccountPatientRepository.class);
    private final NotificationPreferencesService preferences = mock(NotificationPreferencesService.class);
    private final NotificationPublisher publisher =
            new NotificationPublisher(outbox, accountPatients, preferences, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void optedInByDefault() {
        when(preferences.isEnabledFor(any(UUID.class), any(NotificationKind.class))).thenReturn(true);
    }

    @Test
    void enqueueToAccount_writes_one_pending_row() {
        UUID account = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();

        publisher.enqueueToAccount(NotificationKind.BOOKING_REQUESTED, account,
                Map.of("appointmentId", correlation.toString()), correlation);

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outbox).save(captor.capture());
        NotificationOutbox row = captor.getValue();
        assertThat(row.getKind()).isEqualTo(NotificationKind.BOOKING_REQUESTED);
        assertThat(row.getTargetAccountId()).isEqualTo(account);
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(row.getCorrelationId()).isEqualTo(correlation);
        assertThat(row.getPayload()).containsEntry("appointmentId", correlation.toString());
    }

    @Test
    void enqueueToPatientManagers_fans_out_one_row_per_manager() {
        UUID patient = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        when(accountPatients.findManagerAccountIds(patient)).thenReturn(List.of(m1, m2));

        int count = publisher.enqueueToPatientManagers(NotificationKind.TREATMENT_NOTE_ADDED, patient,
                Map.of("noteId", "n1"), UUID.randomUUID());

        assertThat(count).isEqualTo(2);
        verify(outbox, times(2)).save(any(NotificationOutbox.class));
    }

    @Test
    void enqueueToPatientManagers_writes_nothing_when_no_managers() {
        UUID patient = UUID.randomUUID();
        when(accountPatients.findManagerAccountIds(patient)).thenReturn(List.of());

        int count = publisher.enqueueToPatientManagers(NotificationKind.BOOKING_CONFIRMED, patient,
                Map.of("appointmentId", "a1"), UUID.randomUUID());

        assertThat(count).isZero();
        verify(outbox, times(0)).save(any(NotificationOutbox.class));
    }

    @Test
    void enqueueToAccount_skips_a_recipient_who_opted_out() {
        UUID account = UUID.randomUUID();
        when(preferences.isEnabledFor(account, NotificationKind.DISCUSSION_NEW_MESSAGE)).thenReturn(false);

        publisher.enqueueToAccount(NotificationKind.DISCUSSION_NEW_MESSAGE, account,
                Map.of("messageId", "m1"), UUID.randomUUID());

        verify(outbox, never()).save(any(NotificationOutbox.class));
    }

    @Test
    void enqueueToPatientManagers_writes_only_for_opted_in_managers() {
        UUID patient = UUID.randomUUID();
        UUID optedIn = UUID.randomUUID();
        UUID optedOut = UUID.randomUUID();
        when(accountPatients.findManagerAccountIds(patient)).thenReturn(List.of(optedIn, optedOut));
        when(preferences.isEnabledFor(eq(optedOut), any(NotificationKind.class))).thenReturn(false);

        int count = publisher.enqueueToPatientManagers(NotificationKind.BOOKING_CONFIRMED, patient,
                Map.of("appointmentId", "a1"), UUID.randomUUID());

        assertThat(count).isEqualTo(1);
        verify(outbox, times(1)).save(any(NotificationOutbox.class));
    }
}

package com.healyn.notifications;

import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.domain.NotificationPreferences;
import com.healyn.notifications.repository.NotificationPreferencesRepository;
import com.healyn.notifications.service.NotificationPreferencesService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPreferencesServiceTest {

    private final NotificationPreferencesRepository repo = mock(NotificationPreferencesRepository.class);
    private final NotificationPreferencesService service = new NotificationPreferencesService(repo);

    @Test
    void get_returns_all_enabled_defaults_without_persisting_when_none_stored() {
        UUID account = UUID.randomUUID();
        when(repo.findById(account)).thenReturn(Optional.empty());

        NotificationPreferences prefs = service.get(account);

        assertThat(prefs.isAppointmentUpdates()).isTrue();
        assertThat(prefs.isAppointmentReminders()).isTrue();
        assertThat(prefs.isMessages()).isTrue();
        assertThat(prefs.isTreatmentNotes()).isTrue();
        verify(repo, never()).save(any());
    }

    @Test
    void update_creates_row_applying_only_the_provided_fields() {
        UUID account = UUID.randomUUID();
        when(repo.findById(account)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(account, null, null, false, null);

        ArgumentCaptor<NotificationPreferences> captor = ArgumentCaptor.forClass(NotificationPreferences.class);
        verify(repo).save(captor.capture());
        NotificationPreferences saved = captor.getValue();
        assertThat(saved.isMessages()).isFalse();
        assertThat(saved.isAppointmentUpdates()).isTrue();
        assertThat(saved.isAppointmentReminders()).isTrue();
        assertThat(saved.isTreatmentNotes()).isTrue();
    }

    @Test
    void update_leaves_a_null_field_unchanged_on_an_existing_row() {
        UUID account = UUID.randomUUID();
        NotificationPreferences existing = NotificationPreferences.defaultsFor(account);
        existing.apply(false, null, null, null); // appointment_updates already off
        when(repo.findById(account)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(account, null, false, null, null); // only flips reminders

        assertThat(existing.isAppointmentUpdates()).isFalse(); // untouched
        assertThat(existing.isAppointmentReminders()).isFalse();
    }

    @Test
    void isEnabledFor_defaults_to_true_when_no_row_exists() {
        UUID account = UUID.randomUUID();
        when(repo.findById(account)).thenReturn(Optional.empty());

        assertThat(service.isEnabledFor(account, NotificationKind.BOOKING_REQUESTED)).isTrue();
    }

    @Test
    void isEnabledFor_maps_kind_to_its_category() {
        UUID account = UUID.randomUUID();
        NotificationPreferences prefs = NotificationPreferences.defaultsFor(account);
        prefs.apply(false, null, null, null); // disable APPOINTMENT_UPDATES only
        when(repo.findById(account)).thenReturn(Optional.of(prefs));

        // All three booking kinds share the APPOINTMENT_UPDATES category.
        assertThat(service.isEnabledFor(account, NotificationKind.BOOKING_REQUESTED)).isFalse();
        assertThat(service.isEnabledFor(account, NotificationKind.BOOKING_CONFIRMED)).isFalse();
        assertThat(service.isEnabledFor(account, NotificationKind.BOOKING_CANCELLED)).isFalse();
        // A different category is unaffected.
        assertThat(service.isEnabledFor(account, NotificationKind.DISCUSSION_NEW_MESSAGE)).isTrue();
        assertThat(service.isEnabledFor(account, NotificationKind.APPOINTMENT_REMINDER)).isTrue();
        assertThat(service.isEnabledFor(account, NotificationKind.TREATMENT_NOTE_ADDED)).isTrue();
    }
}

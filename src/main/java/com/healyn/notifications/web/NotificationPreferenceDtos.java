package com.healyn.notifications.web;

import com.healyn.notifications.domain.NotificationPreferences;

public final class NotificationPreferenceDtos {

    private NotificationPreferenceDtos() {}

    /** Full snapshot of an account's opt-outs; every field always present. */
    public record PreferencesView(
            boolean appointmentUpdates,
            boolean appointmentReminders,
            boolean messages,
            boolean treatmentNotes) {

        public static PreferencesView from(NotificationPreferences p) {
            return new PreferencesView(p.isAppointmentUpdates(), p.isAppointmentReminders(),
                    p.isMessages(), p.isTreatmentNotes());
        }
    }

    /** Partial update: a {@code null} (omitted) field leaves that category unchanged. */
    public record UpdateRequest(
            Boolean appointmentUpdates,
            Boolean appointmentReminders,
            Boolean messages,
            Boolean treatmentNotes) {}
}

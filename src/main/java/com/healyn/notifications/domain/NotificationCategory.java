package com.healyn.notifications.domain;

/**
 * The user-facing groupings an account can opt out of, each covering one or more
 * {@link NotificationKind} values. This is the unit notification preferences are expressed in
 * (API_STANDARDS §9.8) — users toggle a category, not an internal kind. The mapping is total:
 * every kind belongs to exactly one category.
 */
public enum NotificationCategory {
    APPOINTMENT_UPDATES,
    APPOINTMENT_REMINDERS,
    MESSAGES,
    TREATMENT_NOTES;

    public static NotificationCategory of(NotificationKind kind) {
        return switch (kind) {
            case BOOKING_REQUESTED, BOOKING_CONFIRMED, BOOKING_CANCELLED -> APPOINTMENT_UPDATES;
            case APPOINTMENT_REMINDER -> APPOINTMENT_REMINDERS;
            case DISCUSSION_NEW_MESSAGE -> MESSAGES;
            case TREATMENT_NOTE_ADDED -> TREATMENT_NOTES;
        };
    }
}

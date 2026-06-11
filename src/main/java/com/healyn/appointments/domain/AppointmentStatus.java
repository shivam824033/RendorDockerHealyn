package com.healyn.appointments.domain;

public enum AppointmentStatus {
    REQUESTED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    RESCHEDULED,
    /// The physiotherapist declined a request before it was ever scheduled (REQUESTED →
    /// REJECTED). A first-class terminal state, distinct from a cancellation.
    REJECTED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW
                || this == RESCHEDULED || this == REJECTED;
    }
}

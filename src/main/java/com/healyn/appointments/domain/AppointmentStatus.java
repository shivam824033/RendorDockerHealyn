package com.healyn.appointments.domain;

public enum AppointmentStatus {
    REQUESTED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    RESCHEDULED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW || this == RESCHEDULED;
    }
}

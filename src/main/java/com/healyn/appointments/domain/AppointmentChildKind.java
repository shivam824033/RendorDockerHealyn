package com.healyn.appointments.domain;

/// How a child appointment derived from its lineage. Only actions that spawn a *new bookable
/// row* are children — a reschedule replacement, or a follow-up tied to a prior appointment.
/// In-place lifecycle changes (confirm/start/complete/cancel) are timeline events, not children.
///
/// The {@code suffixLetter} is appended to the lineage root's Appointment Number to number the
/// child (e.g. root {@code PHY-20260610-0001} -> first reschedule {@code PHY-20260610-0001-R1}).
public enum AppointmentChildKind {
    RESCHEDULE("R"),
    FOLLOW_UP("F"),
    REVIEW("V"),
    REOPEN("O");

    private final String suffixLetter;

    AppointmentChildKind(String suffixLetter) {
        this.suffixLetter = suffixLetter;
    }

    public String suffixLetter() {
        return suffixLetter;
    }
}

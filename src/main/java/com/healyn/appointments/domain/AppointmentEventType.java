package com.healyn.appointments.domain;

/// What happened to an appointment, as recorded on its append-only timeline
/// (`appointment_events`, APPOINTMENT_FLOW §3). In-place lifecycle changes and the two
/// sides of a parent-child action (a child's CREATED / the parent's RESCHEDULED) are both
/// events; only the latter also spawn a new appointment row (see {@link AppointmentChildKind}).
/// Mirrors the Postgres enum `appointment_event_type`.
public enum AppointmentEventType {
    /// A new appointment row came into being — a patient request, a follow-up, or a
    /// lineage child (then {@code childKind} / {@code relatedAppointmentId} say from what).
    CREATED,
    /// The physiotherapist assigned the final time to a REQUESTED row (→ CONFIRMED).
    SCHEDULED,
    STARTED,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    /// This row was replaced by a RESCHEDULE child ({@code relatedAppointmentId} = the child).
    RESCHEDULED,
    /// Reserved for the approved request-rejection flow (not emitted yet).
    REJECTED
}

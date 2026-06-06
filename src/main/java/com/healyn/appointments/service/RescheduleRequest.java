package com.healyn.appointments.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/// A reschedule. The shape depends on the initiator (APPOINTMENT_FLOW §6):
/// a physiotherapist sends {@code scheduledAt}/{@code durationMinutes} (new row is CONFIRMED);
/// a patient sends {@code requestedDate}/{@code preferredTime} (new row is an unscheduled
/// REQUESTED re-request). Unused fields for the chosen path are null.
public record RescheduleRequest(
        Instant scheduledAt,
        Short durationMinutes,
        LocalDate requestedDate,
        LocalTime preferredTime,
        String reason) {
}

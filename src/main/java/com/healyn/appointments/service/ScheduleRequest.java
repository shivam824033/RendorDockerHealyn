package com.healyn.appointments.service;

import java.time.Instant;

/// The physiotherapist's assignment of a final time to a REQUESTED appointment.
public record ScheduleRequest(
        Instant scheduledAt,
        short durationMinutes) {
}

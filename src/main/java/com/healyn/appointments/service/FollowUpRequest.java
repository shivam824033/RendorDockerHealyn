package com.healyn.appointments.service;

import java.time.Instant;
import java.util.UUID;

/// A physiotherapist-created follow-up: the physiotherapist sets the date and time directly.
public record FollowUpRequest(
        UUID patientId,
        Instant scheduledAt,
        short durationMinutes,
        String reason) {
}

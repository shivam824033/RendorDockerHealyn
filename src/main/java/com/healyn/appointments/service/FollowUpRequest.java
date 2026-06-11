package com.healyn.appointments.service;

import java.time.Instant;
import java.util.UUID;

/// A physiotherapist-created follow-up: the physiotherapist sets the date and time directly.
/// {@code sourceAppointmentId} is optional — when present, the follow-up is linked as a child of
/// that appointment's lineage (numbered ...-F1, ...-F2); when null it is a standalone follow-up
/// (its own root, with a normal per-day number).
public record FollowUpRequest(
        UUID patientId,
        UUID sourceAppointmentId,
        Instant scheduledAt,
        short durationMinutes,
        String reason) {
}

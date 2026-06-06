package com.healyn.appointments.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/// A patient's appointment request: a mandatory date and an optional time-of-day
/// hint. No final time — the physiotherapist assigns that via {@code /schedule}.
public record BookingRequest(
        UUID patientId,
        LocalDate requestedDate,
        LocalTime preferredTime,
        String reason) {
}

package com.healyn.appointments.service;

import com.healyn.appointments.domain.AppointmentEvent;

/// One timeline event paired with the human-friendly number of the appointment it happened
/// to — the handle users see, since UUIDs are never exposed as the primary identifier.
public record TimelineEntry(AppointmentEvent event, String appointmentNumber) {}

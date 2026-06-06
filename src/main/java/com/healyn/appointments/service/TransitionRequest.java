package com.healyn.appointments.service;

import com.healyn.appointments.domain.AppointmentCancelReason;
import com.healyn.appointments.domain.AppointmentStatus;

public record TransitionRequest(
        AppointmentStatus to,
        AppointmentCancelReason cancelReason,
        String cancelNote) {
}

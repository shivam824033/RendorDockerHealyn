package com.healyn.appointments.web;

import com.healyn.appointments.domain.Appointment;

final class AppointmentMapper {

    private AppointmentMapper() {}

    static AppointmentDtos.AppointmentView toView(Appointment a) {
        return new AppointmentDtos.AppointmentView(
                a.getId(),
                a.getPatientId(),
                a.getBookedByAccountId(),
                a.getPhysiotherapistId(),
                a.getRequestedDate(),
                a.getPreferredTime(),
                a.getScheduledAt(),
                a.getScheduledEndAt(),
                a.getDurationMinutes(),
                a.getStatus(),
                a.isFollowUp(),
                a.getReason(),
                a.getCancelReason(),
                a.getCancelNote(),
                a.getRescheduledFromId(),
                a.getConfirmedAt(),
                a.getStartedAt(),
                a.getCompletedAt(),
                a.getCancelledAt(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}

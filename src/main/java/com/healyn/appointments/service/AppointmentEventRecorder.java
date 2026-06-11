package com.healyn.appointments.service;

import com.healyn.appointments.domain.Appointment;
import com.healyn.appointments.domain.AppointmentEvent;
import com.healyn.appointments.domain.AppointmentEventType;
import com.healyn.appointments.repository.AppointmentEventRepository;
import com.healyn.auth.domain.AccountRole;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Appends timeline entries to {@code appointment_events} inside the caller's transaction,
 * so an event exists if and only if the action it describes committed. Append-only: this
 * is the sole writer and nothing updates or deletes events (APPOINTMENT_FLOW §3).
 */
@Component
public class AppointmentEventRecorder {

    private final AppointmentEventRepository events;

    public AppointmentEventRecorder(AppointmentEventRepository events) {
        this.events = events;
    }

    /// A new row came into being — the lineage link (source / child kind), if any, is read
    /// off the row itself, so root and child creations record uniformly.
    public void recordCreated(Appointment appt, UUID actorId, AccountRole role, Instant now) {
        events.save(new AppointmentEvent(appt.getId(), AppointmentEventType.CREATED, now,
                actorId, role, appt.getSourceAppointmentId(), appt.getChildKind(), null));
    }

    public void recordScheduled(Appointment appt, UUID actorId, AccountRole role, Instant now) {
        events.save(new AppointmentEvent(appt.getId(), AppointmentEventType.SCHEDULED, now,
                actorId, role, null, null, null));
    }

    /// An in-place status transition (STARTED / COMPLETED / CANCELLED / NO_SHOW). The cancel
    /// reason is the enum only — the free-text note stays on the appointments row (PHI-free).
    public void recordTransition(Appointment appt, AppointmentEventType type,
                                 UUID actorId, AccountRole role, Instant now) {
        events.save(new AppointmentEvent(appt.getId(), type, now,
                actorId, role, null, null,
                type == AppointmentEventType.CANCELLED ? appt.getCancelReason() : null));
    }

    /// The replaced side of a reschedule: the old row points at the child that supersedes it.
    public void recordRescheduled(Appointment old, Appointment replacement,
                                  UUID actorId, AccountRole role, Instant now) {
        events.save(new AppointmentEvent(old.getId(), AppointmentEventType.RESCHEDULED, now,
                actorId, role, replacement.getId(), null, null));
    }
}

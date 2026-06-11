package com.healyn.appointments.web;

import com.healyn.appointments.domain.AppointmentCancelReason;
import com.healyn.appointments.domain.AppointmentChildKind;
import com.healyn.appointments.domain.AppointmentEventType;
import com.healyn.appointments.domain.AppointmentStatus;
import com.healyn.auth.domain.AccountRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AppointmentDtos {

    private AppointmentDtos() {}

    /// A patient request: the date is mandatory, the time-of-day is an optional hint.
    /// The patient never sends a final time — the physiotherapist sets it via /schedule.
    public record BookRequest(
            @NotNull UUID patientId,
            @NotNull LocalDate requestedDate,
            LocalTime preferredTime,
            @Size(max = 280) String reason) {}

    public record TransitionRequestBody(
            @NotNull AppointmentStatus to,
            AppointmentCancelReason cancelReason,
            @Size(max = 2000) String cancelNote) {}

    /// The physiotherapist assigns the final time to a REQUESTED appointment.
    public record ScheduleRequestBody(
            @NotNull Instant scheduledAt,
            @NotNull @Min(5) @Max(240) Short durationMinutes) {}

    /// A physiotherapist-created follow-up at a time the physiotherapist sets. The optional
    /// sourceAppointmentId links it as a child of an existing appointment's lineage (numbered -F1…).
    public record FollowUpRequestBody(
            @NotNull UUID patientId,
            UUID sourceAppointmentId,
            @NotNull Instant scheduledAt,
            @NotNull @Min(5) @Max(240) Short durationMinutes,
            @Size(max = 280) String reason) {}

    /// Role-aware: a physiotherapist sends scheduledAt + durationMinutes (new CONFIRMED row);
    /// a patient sends requestedDate + optional preferredTime (new unscheduled REQUESTED). The
    /// service enforces which fields are required for the caller's role.
    public record RescheduleRequestBody(
            Instant scheduledAt,
            @Min(5) @Max(240) Short durationMinutes,
            LocalDate requestedDate,
            LocalTime preferredTime,
            @Size(max = 280) String reason) {}

    public record AppointmentView(
            UUID id,
            String appointmentNumber,
            UUID patientId,
            UUID bookedByAccountId,
            UUID physiotherapistId,
            LocalDate requestedDate,
            LocalTime preferredTime,
            Instant scheduledAt,
            Instant scheduledEndAt,
            short durationMinutes,
            AppointmentStatus status,
            boolean isFollowUp,
            String reason,
            AppointmentCancelReason cancelReason,
            String cancelNote,
            UUID rescheduledFromId,
            UUID rootAppointmentId,
            UUID sourceAppointmentId,
            AppointmentChildKind childKind,
            Instant confirmedAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            Instant createdAt,
            Instant updatedAt) {}

    public record AppointmentPage(List<AppointmentView> items, String nextCursor) {}

    /// A bounded, non-paginated result (upcoming dashboard, month calendar). No cursor: the
    /// caller asks for a capped or range-bounded window and gets the whole window back.
    public record AppointmentList(List<AppointmentView> items) {}

    /// One entry on a lineage's unified timeline. relatedAppointmentId / childKind carry the
    /// parent-child link of a reschedule or follow-up; cancelReason only on CANCELLED. The
    /// client compares actorAccountId with its own to render "you" vs the other party.
    public record TimelineEventView(
            UUID appointmentId,
            String appointmentNumber,
            AppointmentEventType eventType,
            UUID actorAccountId,
            AccountRole actorRole,
            UUID relatedAppointmentId,
            AppointmentChildKind childKind,
            AppointmentCancelReason cancelReason,
            Instant occurredAt) {}

    /// The whole lineage's timeline, oldest first. Unpaginated: a lineage is a handful of
    /// appointments, each contributing a bounded number of lifecycle events.
    public record TimelineView(List<TimelineEventView> items) {}

    /// One global-search suggestion: enough to render an autocomplete row (the appointment's
    /// human-friendly number, its patient's name + number, status and date) and to navigate to
    /// the appointment by id. Deliberately a thin, extensible shape — distinct from the full
    /// AppointmentView so the header autocomplete stays cheap and future match types can be added.
    public record AppointmentSuggestion(
            UUID appointmentId,
            String appointmentNumber,
            UUID patientId,
            String patientName,
            String patientNumber,
            AppointmentStatus status,
            Instant scheduledAt,
            LocalDate requestedDate) {}

    /// A bounded list of search suggestions, most recent first. Wrapper (not a bare list) so the
    /// result can later grow facets / a cursor without breaking the wire shape.
    public record SearchResults(List<AppointmentSuggestion> items) {}
}

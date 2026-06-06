package com.healyn.appointments.domain;

import com.healyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class Appointment extends BaseEntity {

    /// Placeholder duration for an unscheduled request: the column is NOT NULL with a
    /// 5–240 CHECK, so a request needs a valid value until the physiotherapist sets the
    /// real one at schedule time. The slot default (30) is the natural placeholder.
    private static final short DEFAULT_DURATION_MINUTES = 30;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "booked_by_account_id", nullable = false, updatable = false)
    private UUID bookedByAccountId;

    @Column(name = "physiotherapist_id", nullable = false, updatable = false)
    private UUID physiotherapistId;

    @Column(name = "requested_date", nullable = false, updatable = false)
    private LocalDate requestedDate;

    @Column(name = "preferred_time", updatable = false)
    private LocalTime preferredTime;

    // Null until the physiotherapist schedules the request (they set the final time).
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "scheduled_end_at")
    private Instant scheduledEndAt;

    @Column(name = "duration_minutes", nullable = false)
    private short durationMinutes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "appointment_status")
    private AppointmentStatus status;

    @Column(name = "is_follow_up", nullable = false, updatable = false)
    private boolean followUp;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "cancel_reason", columnDefinition = "appointment_cancel_reason")
    private AppointmentCancelReason cancelReason;

    @Column(name = "cancel_note")
    private String cancelNote;

    @Column(name = "rescheduled_from_id", updatable = false)
    private UUID rescheduledFromId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Appointment() {}

    /// A scheduled appointment created with a concrete time (physio reschedule, follow-up
    /// seeding, tests). `requested_date` is derived from the scheduled instant (stored UTC)
    /// so the request-first column is always populated. Status starts at REQUESTED — callers
    /// that need it CONFIRMED (e.g. a follow-up) call {@link #schedule}.
    public Appointment(UUID id,
                       UUID patientId,
                       UUID bookedByAccountId,
                       UUID physiotherapistId,
                       Instant scheduledAt,
                       short durationMinutes,
                       String reason,
                       UUID rescheduledFromId) {
        this.id = id;
        this.patientId = patientId;
        this.bookedByAccountId = bookedByAccountId;
        this.physiotherapistId = physiotherapistId;
        this.scheduledAt = scheduledAt;
        this.scheduledEndAt = scheduledAt.plus(durationMinutes, ChronoUnit.MINUTES);
        this.durationMinutes = durationMinutes;
        this.requestedDate = scheduledAt.atZone(ZoneOffset.UTC).toLocalDate();
        this.reason = reason;
        this.rescheduledFromId = rescheduledFromId;
        this.followUp = false;
        this.status = AppointmentStatus.REQUESTED;
    }

    /// A patient request with no time yet: the patient picks a date (and an optional
    /// time-of-day hint); the physiotherapist assigns the final time later via
    /// {@link #schedule}. Status is REQUESTED, `scheduled_at` is null.
    public static Appointment request(UUID id,
                                      UUID patientId,
                                      UUID bookedByAccountId,
                                      UUID physiotherapistId,
                                      LocalDate requestedDate,
                                      LocalTime preferredTime,
                                      String reason,
                                      UUID rescheduledFromId) {
        Appointment a = new Appointment();
        a.id = id;
        a.patientId = patientId;
        a.bookedByAccountId = bookedByAccountId;
        a.physiotherapistId = physiotherapistId;
        a.requestedDate = requestedDate;
        a.preferredTime = preferredTime;
        a.durationMinutes = DEFAULT_DURATION_MINUTES;
        a.reason = reason;
        a.rescheduledFromId = rescheduledFromId;
        a.followUp = false;
        a.status = AppointmentStatus.REQUESTED;
        return a;
    }

    /// A physiotherapist-created follow-up at a concrete time: the physiotherapist sets the
    /// date and time directly, so it starts CONFIRMED with `is_follow_up = true`. There is no
    /// patient request step (APPOINTMENT_FLOW §6a). `requested_date` is derived from the
    /// scheduled instant (stored UTC).
    public static Appointment followUp(UUID id,
                                       UUID patientId,
                                       UUID bookedByAccountId,
                                       UUID physiotherapistId,
                                       Instant scheduledAt,
                                       short durationMinutes,
                                       String reason,
                                       Instant now) {
        Appointment a = new Appointment();
        a.id = id;
        a.patientId = patientId;
        a.bookedByAccountId = bookedByAccountId;
        a.physiotherapistId = physiotherapistId;
        a.requestedDate = scheduledAt.atZone(ZoneOffset.UTC).toLocalDate();
        a.reason = reason;
        a.followUp = true;
        a.schedule(scheduledAt, durationMinutes, now);
        return a;
    }

    public UUID getPatientId() { return patientId; }
    public UUID getBookedByAccountId() { return bookedByAccountId; }
    public UUID getPhysiotherapistId() { return physiotherapistId; }
    public LocalDate getRequestedDate() { return requestedDate; }
    public LocalTime getPreferredTime() { return preferredTime; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getScheduledEndAt() { return scheduledEndAt; }
    public short getDurationMinutes() { return durationMinutes; }
    public AppointmentStatus getStatus() { return status; }
    public boolean isFollowUp() { return followUp; }
    public String getReason() { return reason; }
    public AppointmentCancelReason getCancelReason() { return cancelReason; }
    public String getCancelNote() { return cancelNote; }
    public UUID getRescheduledFromId() { return rescheduledFromId; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getDeletedAt() { return deletedAt; }

    /// The physiotherapist assigns the final time to a request and confirms it in one step:
    /// REQUESTED → CONFIRMED. The caller (service layer) checks the source status first and
    /// flushes so the physio-overlap EXCLUDE constraint can reject a clashing time.
    public void schedule(Instant scheduledAt, short durationMinutes, Instant now) {
        this.scheduledAt = scheduledAt;
        this.scheduledEndAt = scheduledAt.plus(durationMinutes, ChronoUnit.MINUTES);
        this.durationMinutes = durationMinutes;
        this.status = AppointmentStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    /// Flags this not-yet-persisted row as a follow-up. `is_follow_up` is insert-only
    /// (`updatable = false`), so this only takes effect when called before the first save —
    /// used when a physio reschedule must carry the follow-up nature onto the replacement row.
    public void markFollowUp() {
        this.followUp = true;
    }

    public void start(Instant now) {
        this.status = AppointmentStatus.IN_PROGRESS;
        this.startedAt = now;
    }

    public void complete(Instant now) {
        this.status = AppointmentStatus.COMPLETED;
        this.completedAt = now;
    }

    public void cancel(Instant now, AppointmentCancelReason reason, String note) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancelReason = reason;
        this.cancelNote = note;
    }

    public void markNoShow() {
        this.status = AppointmentStatus.NO_SHOW;
    }

    public void markRescheduled() {
        this.status = AppointmentStatus.RESCHEDULED;
    }
}

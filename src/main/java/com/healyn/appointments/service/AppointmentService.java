package com.healyn.appointments.service;

import com.healyn.appointments.domain.Appointment;
import com.healyn.appointments.domain.AppointmentStatus;
import com.healyn.appointments.policy.AppointmentAccessPolicy;
import com.healyn.appointments.repository.AppointmentRepository;
import com.healyn.audit.domain.AuditAction;
import com.healyn.audit.domain.AuditResource;
import com.healyn.audit.service.AuditLogger;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.common.error.ConflictException;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnprocessableException;
import com.healyn.common.id.UuidV7;
import com.healyn.common.pagination.Cursor;
import com.healyn.common.pagination.CursorPage;
import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.service.NotificationPublisher;
import com.healyn.patients.repository.AccountPatientRepository;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final String PG_EXCLUSION_VIOLATION = "23P01";
    private static final Duration BOOKING_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Duration BOOKING_MAX_HORIZON = Duration.ofDays(90);
    private static final short MIN_DURATION = 5;
    private static final short MAX_DURATION = 240;
    private static final int UPCOMING_DEFAULT_LIMIT = 30;
    private static final int UPCOMING_MAX_LIMIT = 50;
    private static final Duration CALENDAR_MAX_RANGE = Duration.ofDays(62);
    // Never-matching placeholders bound to the IN lists when a filter is disabled.
    private static final Collection<UUID> PATIENT_FILTER_SENTINEL = List.of(new UUID(0L, 0L));
    private static final Collection<AppointmentStatus> STATUS_FILTER_SENTINEL =
            List.of(AppointmentStatus.REQUESTED);
    // Live scheduled rows for the Upcoming-30 dashboard; the calendar additionally surfaces
    // past real events (COMPLETED / NO_SHOW) so a month grid shows history too.
    private static final Collection<AppointmentStatus> UPCOMING_STATUSES =
            List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS);
    private static final Collection<AppointmentStatus> CALENDAR_STATUSES =
            List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS,
                    AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);

    private final AppointmentRepository appointments;
    private final AccountRepository accounts;
    private final AccountPatientRepository accountPatients;
    private final AppointmentAccessPolicy access;
    private final IdempotencyGuard idempotency;
    private final NotificationPublisher notifications;
    private final AuditLogger audit;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments,
                              AccountRepository accounts,
                              AccountPatientRepository accountPatients,
                              AppointmentAccessPolicy access,
                              IdempotencyGuard idempotency,
                              NotificationPublisher notifications,
                              AuditLogger audit,
                              Clock clock) {
        this.appointments = appointments;
        this.accounts = accounts;
        this.accountPatients = accountPatients;
        this.access = access;
        this.idempotency = idempotency;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Appointment book(UUID actorId, AccountRole role, BookingRequest req, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new UnprocessableException(ErrorCode.COMMON_IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key header is required");
        }
        Optional<UUID> replay = idempotency.lookup(actorId, idempotencyKey);
        if (replay.isPresent()) {
            return appointments.findByIdAndDeletedAtIsNull(replay.get())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.APPOINTMENT_NOT_FOUND,
                            "Appointment not found"));
        }

        access.requireBook(actorId, role, req.patientId());
        UUID physioId = resolvePhysioId();
        validateRequestedDate(req.requestedDate());

        // Request-first: no time and no availability check — a patient may request any
        // date regardless of whether that day's slots are already taken. The
        // physiotherapist assigns the final time later via schedule() (APPOINTMENT_FLOW §2).
        Appointment appt = Appointment.request(
                UuidV7.generate(),
                req.patientId(),
                actorId,
                physioId,
                req.requestedDate(),
                req.preferredTime(),
                req.reason(),
                null);
        Appointment saved = appointments.save(appt);
        idempotency.store(actorId, idempotencyKey, saved.getId());
        notifications.enqueueToAccount(NotificationKind.BOOKING_REQUESTED, saved.getPhysiotherapistId(),
                Map.of("appointmentId", saved.getId().toString()), saved.getId());
        audit.record(AuditAction.CREATE, actorId, role, AuditResource.APPOINTMENT, saved.getId(),
                Map.of("patientId", saved.getPatientId().toString()));
        return saved;
    }

    /// The physiotherapist assigns the final time to a REQUESTED request and confirms it in one
    /// step (APPOINTMENT_FLOW §2). The patient never sets the time. The CONFIRMED row enters the
    /// physio-overlap EXCLUDE set on flush, so a clash with an existing appointment returns 409.
    @Transactional
    public Appointment schedule(UUID actorId, AccountRole role, UUID appointmentId, ScheduleRequest req) {
        access.requireSchedule(role);
        Appointment appt = loadActive(appointmentId);
        if (appt.getStatus() != AppointmentStatus.REQUESTED) {
            throw new ConflictException(ErrorCode.APPOINTMENT_INVALID_TRANSITION,
                    "Only a REQUESTED appointment can be scheduled");
        }
        validateSchedule(req.scheduledAt(), req.durationMinutes());
        appt.schedule(req.scheduledAt(), req.durationMinutes(), Instant.now(clock));

        Appointment result = saveAndFlushOrConflict(appt);
        notifications.enqueueToPatientManagers(NotificationKind.BOOKING_CONFIRMED,
                result.getPatientId(), payload(result), result.getId());
        audit.record(AuditAction.UPDATE, actorId, role, AuditResource.APPOINTMENT, result.getId(),
                Map.of("status", AppointmentStatus.CONFIRMED.name()));
        return result;
    }

    /// The physiotherapist creates a follow-up at a time they set (APPOINTMENT_FLOW §6a): a brand
    /// new CONFIRMED row with `is_follow_up = true`, subject to the same physio-overlap guard.
    @Transactional
    public Appointment createFollowUp(UUID actorId, AccountRole role, FollowUpRequest req) {
        access.requireCreateFollowUp(role);
        validateSchedule(req.scheduledAt(), req.durationMinutes());
        Appointment fu = Appointment.followUp(
                UuidV7.generate(),
                req.patientId(),
                actorId,
                actorId,
                req.scheduledAt(),
                req.durationMinutes(),
                req.reason(),
                Instant.now(clock));

        Appointment saved = saveAndFlushOrConflict(fu);
        notifications.enqueueToPatientManagers(NotificationKind.BOOKING_CONFIRMED,
                saved.getPatientId(), payload(saved), saved.getId());
        audit.record(AuditAction.CREATE, actorId, role, AuditResource.APPOINTMENT, saved.getId(),
                Map.of("patientId", saved.getPatientId().toString(), "followUp", "true"));
        return saved;
    }

    @Transactional(readOnly = true)
    public Appointment get(UUID actorId, AccountRole role, UUID appointmentId) {
        Appointment appt = loadActive(appointmentId);
        access.requireRead(actorId, role, appt);
        return appt;
    }

    @Transactional(readOnly = true)
    public CursorPage<Appointment> list(UUID actorId, AccountRole role,
                                        UUID patientIdFilter,
                                        Collection<AppointmentStatus> statuses,
                                        Instant from, Instant to,
                                        String cursorToken, int limit) {
        if (limit <= 0 || limit > 50) limit = 20;
        Collection<UUID> patientIds = resolvePatientIdScope(actorId, role, patientIdFilter);
        // empty collection means "no patient visible" => empty page.
        if (patientIds != null && patientIds.isEmpty()) {
            return new CursorPage<>(List.of(), null);
        }
        Collection<AppointmentStatus> statusFilter =
                (statuses == null || statuses.isEmpty()) ? null : statuses;

        // Toggle each optional filter with a boolean flag; when a filter is off its companion
        // param is bound to a harmless sentinel so the IN list stays valid SQL (see repository).
        boolean filterPatients = patientIds != null;
        Collection<UUID> patientParam = filterPatients ? patientIds : PATIENT_FILTER_SENTINEL;
        boolean filterStatuses = statusFilter != null;
        Collection<AppointmentStatus> statusParam = filterStatuses ? statusFilter : STATUS_FILTER_SENTINEL;
        boolean filterFrom = from != null;
        boolean filterTo = to != null;

        Limit lim = Limit.of(limit + 1);
        List<Appointment> rows;
        if (cursorToken == null || cursorToken.isBlank()) {
            rows = appointments.listFirstPage(
                    filterPatients, patientParam, filterStatuses, statusParam,
                    filterFrom, from, filterTo, to, lim);
        } else {
            Cursor c = Cursor.decode(cursorToken);
            rows = appointments.listAfterCursor(
                    filterPatients, patientParam, filterStatuses, statusParam,
                    filterFrom, from, filterTo, to, c.pivot(), c.id(), lim);
        }

        String nextCursor = null;
        if (rows.size() > limit) {
            Appointment pivot = rows.get(limit - 1);
            nextCursor = new Cursor(pivot.getScheduledAt(), pivot.getId()).encode();
            rows = rows.subList(0, limit);
        }
        return new CursorPage<>(new ArrayList<>(rows), nextCursor);
    }

    /// The Upcoming-30 dashboard (APPOINTMENT_FLOW §6a): the next live scheduled appointments
    /// from now, ascending. Unscheduled REQUESTED rows have no time and are not "upcoming" — the
    /// physiotherapist works those off the REQUESTED list, not this one.
    @Transactional(readOnly = true)
    public List<Appointment> upcoming(UUID actorId, AccountRole role, int limit) {
        if (limit <= 0 || limit > UPCOMING_MAX_LIMIT) limit = UPCOMING_DEFAULT_LIMIT;
        Collection<UUID> scope = resolvePatientIdScope(actorId, role, null);
        if (scope != null && scope.isEmpty()) return List.of();
        boolean filterPatients = scope != null;
        Collection<UUID> patientParam = filterPatients ? scope : PATIENT_FILTER_SENTINEL;
        return appointments.findUpcoming(
                UPCOMING_STATUSES, Instant.now(clock), filterPatients, patientParam, Limit.of(limit));
    }

    /// The Today-screen month calendar: every scheduled appointment in a bounded instant window,
    /// ascending. The caller (mobile) computes the window's edges in its own timezone and sends
    /// instants, so day-boundary placement stays correct. The range is capped to keep the
    /// unpaginated result bounded.
    @Transactional(readOnly = true)
    public List<Appointment> calendar(UUID actorId, AccountRole role, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "from and to are required and from must be before to");
        }
        if (Duration.between(from, to).compareTo(CALENDAR_MAX_RANGE) > 0) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "calendar range cannot exceed " + CALENDAR_MAX_RANGE.toDays() + " days");
        }
        Collection<UUID> scope = resolvePatientIdScope(actorId, role, null);
        if (scope != null && scope.isEmpty()) return List.of();
        boolean filterPatients = scope != null;
        Collection<UUID> patientParam = filterPatients ? scope : PATIENT_FILTER_SENTINEL;
        return appointments.findScheduledInRange(CALENDAR_STATUSES, from, to, filterPatients, patientParam);
    }

    @Transactional
    public Appointment transition(UUID actorId, AccountRole role, UUID appointmentId, TransitionRequest req) {
        Appointment appt = loadActive(appointmentId);
        access.requireTransition(actorId, role, appt, req.to());
        AppointmentTransitions.requireAllowed(appt.getStatus(), req.to());

        Instant now = Instant.now(clock);
        switch (req.to()) {
            case IN_PROGRESS -> appt.start(now);
            case COMPLETED -> appt.complete(now);
            case CANCELLED -> {
                if (req.cancelReason() == null) {
                    throw new UnprocessableException(ErrorCode.APPOINTMENT_CANCEL_REASON_REQUIRED,
                            "cancelReason is required when cancelling");
                }
                appt.cancel(now, req.cancelReason(), req.cancelNote());
            }
            case NO_SHOW -> appt.markNoShow();
            default -> throw new ConflictException(ErrorCode.APPOINTMENT_INVALID_TRANSITION,
                    "Cannot transition to " + req.to() + " via this endpoint");
        }

        Appointment result = saveAndFlushOrConflict(appt);
        notifyTransition(result, role, req.to());
        audit.record(AuditAction.UPDATE, actorId, role, AuditResource.APPOINTMENT, result.getId(),
                Map.of("status", req.to().name()));
        return result;
    }

    private void notifyTransition(Appointment appt, AccountRole actorRole, AppointmentStatus to) {
        Map<String, String> payload = payload(appt);
        switch (to) {
            case CANCELLED -> {
                // Notify the counterparty: physio's action reaches the patient side, and vice versa.
                if (actorRole == AccountRole.ROLE_PHYSIO) {
                    notifications.enqueueToPatientManagers(
                            NotificationKind.BOOKING_CANCELLED, appt.getPatientId(), payload, appt.getId());
                } else {
                    notifications.enqueueToAccount(
                            NotificationKind.BOOKING_CANCELLED, appt.getPhysiotherapistId(), payload, appt.getId());
                }
            }
            default -> { /* IN_PROGRESS / COMPLETED / NO_SHOW have no push in Phase 1 */ }
        }
    }

    /// Rescheduling always creates a new row and marks the old one RESCHEDULED, in one
    /// transaction (APPOINTMENT_FLOW §6). The initiator decides the new row's shape: the
    /// physiotherapist sets a concrete time (new CONFIRMED row); a patient re-requests a date
    /// (new unscheduled REQUESTED row, the physiotherapist assigns the time later).
    @Transactional
    public Appointment reschedule(UUID actorId, AccountRole role, UUID appointmentId, RescheduleRequest req) {
        Appointment old = loadActive(appointmentId);
        access.requireReschedule(actorId, role, old);
        if (old.getStatus() != AppointmentStatus.REQUESTED && old.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ConflictException(ErrorCode.APPOINTMENT_INVALID_TRANSITION,
                    "Only REQUESTED or CONFIRMED appointments can be rescheduled");
        }
        Instant now = Instant.now(clock);
        String reason = req.reason() != null ? req.reason() : old.getReason();

        Appointment fresh;
        NotificationKind kind;
        if (role == AccountRole.ROLE_PHYSIO) {
            short duration = requireDuration(req.durationMinutes());
            validateSchedule(req.scheduledAt(), duration);
            fresh = new Appointment(UuidV7.generate(), old.getPatientId(), actorId,
                    old.getPhysiotherapistId(), req.scheduledAt(), duration, reason, old.getId());
            fresh.schedule(req.scheduledAt(), duration, now);
            if (old.isFollowUp()) {
                fresh.markFollowUp();
            }
            kind = NotificationKind.BOOKING_CONFIRMED;
        } else {
            validateRequestedDate(req.requestedDate());
            fresh = Appointment.request(UuidV7.generate(), old.getPatientId(), actorId,
                    old.getPhysiotherapistId(), req.requestedDate(), req.preferredTime(), reason, old.getId());
            kind = NotificationKind.BOOKING_REQUESTED;
        }

        Appointment saved = saveAndFlushOrConflict(fresh);
        old.markRescheduled();
        appointments.save(old);

        if (kind == NotificationKind.BOOKING_CONFIRMED) {
            notifications.enqueueToPatientManagers(kind, saved.getPatientId(), payload(saved), saved.getId());
        } else {
            notifications.enqueueToAccount(kind, saved.getPhysiotherapistId(), payload(saved), saved.getId());
        }
        audit.record(AuditAction.CREATE, actorId, role, AuditResource.APPOINTMENT, saved.getId(),
                Map.of("rescheduledFromId", old.getId().toString()));
        audit.record(AuditAction.UPDATE, actorId, role, AuditResource.APPOINTMENT, old.getId(),
                Map.of("status", AppointmentStatus.RESCHEDULED.name()));
        return saved;
    }

    // ---- helpers ----

    private Appointment loadActive(UUID id) {
        return appointments.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.APPOINTMENT_NOT_FOUND,
                        "Appointment not found"));
    }

    /// Persists and flushes so the physio-overlap EXCLUDE constraint can reject a clashing time
    /// synchronously, translating the Postgres exclusion violation (23P01) into a 409.
    private Appointment saveAndFlushOrConflict(Appointment appt) {
        try {
            return appointments.saveAndFlush(appt);
        } catch (DataIntegrityViolationException e) {
            if (isExclusionViolation(e)) {
                throw new ConflictException(ErrorCode.APPOINTMENT_SLOT_UNAVAILABLE,
                        "The chosen time overlaps another appointment for the physiotherapist");
            }
            throw e;
        }
    }

    private static Map<String, String> payload(Appointment appt) {
        return Map.of("appointmentId", appt.getId().toString());
    }

    private UUID resolvePhysioId() {
        return accounts.findFirstByRoleAndDeletedAtIsNullOrderByCreatedAtAsc(AccountRole.ROLE_PHYSIO)
                .orElseThrow(() -> new NotFoundException(ErrorCode.APPOINTMENT_SLOT_UNAVAILABLE,
                        "No physiotherapist is configured"))
                .getId();
    }

    private void validateRequestedDate(LocalDate requestedDate) {
        if (requestedDate == null) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "requestedDate is required");
        }
        LocalDate today = LocalDate.now(clock);
        if (requestedDate.isBefore(today)) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "requestedDate cannot be in the past");
        }
        if (requestedDate.isAfter(today.plusDays(BOOKING_MAX_HORIZON.toDays()))) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "requestedDate is more than " + BOOKING_MAX_HORIZON.toDays() + " days in the future");
        }
    }

    private short requireDuration(Short durationMinutes) {
        if (durationMinutes == null) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "durationMinutes is required when a physiotherapist reschedules");
        }
        return durationMinutes;
    }

    private void validateSchedule(Instant scheduledAt, short durationMinutes) {
        if (scheduledAt == null) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "scheduledAt is required");
        }
        if (durationMinutes < MIN_DURATION || durationMinutes > MAX_DURATION) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "durationMinutes must be between " + MIN_DURATION + " and " + MAX_DURATION);
        }
        Instant now = Instant.now(clock);
        if (scheduledAt.isBefore(now.minus(BOOKING_CLOCK_SKEW))) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "scheduledAt cannot be in the past");
        }
        if (scheduledAt.isAfter(now.plus(BOOKING_MAX_HORIZON))) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "scheduledAt is more than 90 days in the future");
        }
    }

    private Collection<UUID> resolvePatientIdScope(UUID actorId, AccountRole role, UUID patientIdFilter) {
        if (role == AccountRole.ROLE_PHYSIO) {
            return patientIdFilter == null ? null : List.of(patientIdFilter);
        }
        Set<UUID> linked = accountPatients.findActivePatientsForAccount(actorId).stream()
                .map(p -> p.getId())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (patientIdFilter != null) {
            if (!linked.contains(patientIdFilter)) {
                return List.of(); // unauthorized filter -> empty page (avoid leaking existence).
            }
            return List.of(patientIdFilter);
        }
        return linked;
    }

    private static boolean isExclusionViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof PSQLException psql && PG_EXCLUSION_VIOLATION.equals(psql.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

}

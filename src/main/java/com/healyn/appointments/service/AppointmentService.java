package com.healyn.appointments.service;

import com.healyn.appointments.domain.Appointment;
import com.healyn.appointments.domain.AppointmentChildKind;
import com.healyn.appointments.domain.AppointmentEventType;
import com.healyn.appointments.domain.AppointmentStatus;
import com.healyn.appointments.policy.AppointmentAccessPolicy;
import com.healyn.appointments.repository.AppointmentEventRepository;
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
import com.healyn.patients.domain.Patient;
import com.healyn.patients.repository.AccountPatientRepository;
import com.healyn.patients.repository.PatientRepository;
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
import java.util.Locale;
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
    // Global search: short terms ('a') would match almost everything, so require two chars.
    private static final int SEARCH_MIN_QUERY_LENGTH = 2;
    private static final int SEARCH_DEFAULT_LIMIT = 10;
    private static final int SEARCH_MAX_LIMIT = 20;
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
    private final PatientRepository patients;
    private final AppointmentAccessPolicy access;
    private final IdempotencyGuard idempotency;
    private final NotificationPublisher notifications;
    private final AuditLogger audit;
    private final AppointmentNumberGenerator numbers;
    private final AppointmentEventRecorder events;
    private final AppointmentEventRepository eventRepository;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments,
                              AccountRepository accounts,
                              AccountPatientRepository accountPatients,
                              PatientRepository patients,
                              AppointmentAccessPolicy access,
                              IdempotencyGuard idempotency,
                              NotificationPublisher notifications,
                              AuditLogger audit,
                              AppointmentNumberGenerator numbers,
                              AppointmentEventRecorder events,
                              AppointmentEventRepository eventRepository,
                              Clock clock) {
        this.appointments = appointments;
        this.accounts = accounts;
        this.accountPatients = accountPatients;
        this.patients = patients;
        this.access = access;
        this.idempotency = idempotency;
        this.notifications = notifications;
        this.audit = audit;
        this.numbers = numbers;
        this.events = events;
        this.eventRepository = eventRepository;
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
        appt.assignNumber(numbers.generate());
        Appointment saved = appointments.save(appt);
        events.recordCreated(saved, actorId, role, Instant.now(clock));
        idempotency.store(actorId, idempotencyKey, saved.getId());
        notifications.enqueueToAccount(NotificationKind.BOOKING_REQUESTED, saved.getPhysiotherapistId(),
                payload(saved), saved.getId());
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
        Instant now = Instant.now(clock);
        appt.schedule(req.scheduledAt(), req.durationMinutes(), now);

        Appointment result = saveAndFlushOrConflict(appt);
        events.recordScheduled(result, actorId, role, now);
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
        Instant now = Instant.now(clock);
        Appointment fu = Appointment.followUp(
                UuidV7.generate(),
                req.patientId(),
                actorId,
                actorId,
                req.scheduledAt(),
                req.durationMinutes(),
                req.reason(),
                now);
        assignFollowUpNumber(fu, req.sourceAppointmentId(), req.patientId());

        Appointment saved = saveAndFlushOrConflict(fu);
        events.recordCreated(saved, actorId, role, now);
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
                                        Boolean isFollowUp,
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
        // Tri-state: null = either; the flag toggles a boolean equality (false binds harmlessly).
        boolean filterFollowUp = isFollowUp != null;
        boolean followUpParam = Boolean.TRUE.equals(isFollowUp);
        boolean filterFrom = from != null;
        boolean filterTo = to != null;

        Limit lim = Limit.of(limit + 1);
        List<Appointment> rows;
        if (cursorToken == null || cursorToken.isBlank()) {
            rows = appointments.listFirstPage(
                    filterPatients, patientParam, filterStatuses, statusParam,
                    filterFollowUp, followUpParam, filterFrom, from, filterTo, to, lim);
        } else {
            Cursor c = Cursor.decode(cursorToken);
            rows = appointments.listAfterCursor(
                    filterPatients, patientParam, filterStatuses, statusParam,
                    filterFollowUp, followUpParam, filterFrom, from, filterTo, to, c.pivot(), c.id(), lim);
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
            // Declining a request: physio-only (enforced above), optional free-text note.
            case REJECTED -> appt.reject(req.cancelNote());
            default -> throw new ConflictException(ErrorCode.APPOINTMENT_INVALID_TRANSITION,
                    "Cannot transition to " + req.to() + " via this endpoint");
        }

        Appointment result = saveAndFlushOrConflict(appt);
        events.recordTransition(result, eventTypeFor(req.to()), actorId, role, now);
        notifyTransition(result, role, req.to());
        audit.record(AuditAction.UPDATE, actorId, role, AuditResource.APPOINTMENT, result.getId(),
                Map.of("status", req.to().name()));
        return result;
    }

    /// Timeline entry for an in-place transition. Callers reach this only after
    /// {@code transition()} has narrowed the target to these four states.
    private static AppointmentEventType eventTypeFor(AppointmentStatus to) {
        return switch (to) {
            case IN_PROGRESS -> AppointmentEventType.STARTED;
            case COMPLETED -> AppointmentEventType.COMPLETED;
            case CANCELLED -> AppointmentEventType.CANCELLED;
            case NO_SHOW -> AppointmentEventType.NO_SHOW;
            case REJECTED -> AppointmentEventType.REJECTED;
            default -> throw new IllegalStateException("No timeline event for transition to " + to);
        };
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
            // A rejection is always physio→patient; reuse the cancelled kind (no separate
            // notification_kind value — the patient's booking did not happen either way).
            case REJECTED -> notifications.enqueueToPatientManagers(
                    NotificationKind.BOOKING_CANCELLED, appt.getPatientId(), payload, appt.getId());
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
        // The replacement is a reschedule child of the old row's lineage, numbered ...-R{n}.
        fresh.linkToParent(old, AppointmentChildKind.RESCHEDULE);
        long priorReschedules = appointments.countByRootAppointmentIdAndChildKind(
                fresh.getRootAppointmentId(), AppointmentChildKind.RESCHEDULE);
        fresh.assignNumber(numbers.childNumber(
                old.getAppointmentNumber(), AppointmentChildKind.RESCHEDULE, priorReschedules));

        Appointment saved = saveAndFlushOrConflict(fresh);
        old.markRescheduled();
        appointments.save(old);
        // Both sides of the replacement, in story order: the old row was rescheduled,
        // then its replacement came into being (same instant; insertion order ties).
        events.recordRescheduled(old, saved, actorId, role, now);
        events.recordCreated(saved, actorId, role, now);

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

    /// The unified timeline of the appointment's whole lineage (APPOINTMENT_FLOW §3): every
    /// event of every live appointment sharing this row's root, oldest first, each paired with
    /// its appointment's human-friendly number. Reading any member shows the full story.
    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(UUID actorId, AccountRole role, UUID appointmentId) {
        Appointment appt = loadActive(appointmentId);
        access.requireRead(actorId, role, appt);
        Map<UUID, String> numbersById = appointments
                .findByRootAppointmentIdAndDeletedAtIsNull(appt.getRootAppointmentId())
                .stream()
                .collect(Collectors.toMap(Appointment::getId, Appointment::getAppointmentNumber));
        return eventRepository.findLineageTimeline(appt.getRootAppointmentId()).stream()
                .map(e -> new TimelineEntry(e, numbersById.get(e.getAppointmentId())))
                .toList();
    }

    /// Global appointment search for the header autocomplete (API_STANDARDS §9.4). Matches the
    /// term against the appointment number / patient number as a prefix and the patient name as
    /// a substring, scoped to the actor's own patients (a physiotherapist sees every patient; an
    /// account only the patients it manages — the same scope as {@link #list}). Each hit carries
    /// its patient's display fields, resolved in one bounded lookup. Returns at most the capped
    /// limit, most recent first; a term shorter than two characters yields nothing.
    @Transactional(readOnly = true)
    public List<AppointmentSearchResult> search(UUID actorId, AccountRole role, String q, int limit) {
        int capped = (limit <= 0 || limit > SEARCH_MAX_LIMIT) ? SEARCH_DEFAULT_LIMIT : limit;
        String term = q == null ? "" : q.trim();
        if (term.length() < SEARCH_MIN_QUERY_LENGTH) return List.of();

        Collection<UUID> scope = resolvePatientIdScope(actorId, role, null);
        if (scope != null && scope.isEmpty()) return List.of();
        boolean filterPatients = scope != null;
        Collection<UUID> patientParam = filterPatients ? scope : PATIENT_FILTER_SENTINEL;

        String escaped = escapeLike(term);
        // Identifiers are stored upper-case; upper-case the term so a case-sensitive LIKE
        // hits the text_pattern_ops indexes. Names use ILIKE (the trigram index is case-folding).
        String numberPrefix = escaped.toUpperCase(Locale.ROOT) + "%";
        String nameContains = "%" + escaped + "%";

        List<Appointment> hits =
                appointments.search(filterPatients, patientParam, numberPrefix, nameContains, capped);
        if (hits.isEmpty()) return List.of();

        Set<UUID> patientIds = hits.stream().map(Appointment::getPatientId).collect(Collectors.toSet());
        Map<UUID, Patient> patientsById = patients.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, p -> p));
        return hits.stream()
                .map(a -> {
                    Patient p = patientsById.get(a.getPatientId());
                    return new AppointmentSearchResult(a,
                            p == null ? null : p.getFullName(),
                            p == null ? null : p.getPatientNumber());
                })
                .toList();
    }

    // ---- helpers ----

    /// Escapes the LIKE/ILIKE wildcards (`%`, `_`) and the escape char itself so a typed term is
    /// matched literally — a user typing "%" searches for a percent sign, not "match anything".
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

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

    /// A follow-up linked to a source becomes a child of that source's lineage (numbered ...-F{n});
    /// without a source it is a standalone follow-up with a normal per-day number. The source must
    /// belong to the same patient, so a lineage never mixes patients.
    private void assignFollowUpNumber(Appointment followUp, UUID sourceAppointmentId, UUID patientId) {
        if (sourceAppointmentId == null) {
            followUp.assignNumber(numbers.generate());
            return;
        }
        Appointment source = loadActive(sourceAppointmentId);
        if (!source.getPatientId().equals(patientId)) {
            throw new UnprocessableException(ErrorCode.APPOINTMENT_INVALID_SCHEDULE,
                    "sourceAppointmentId must belong to the same patient as the follow-up");
        }
        followUp.linkToParent(source, AppointmentChildKind.FOLLOW_UP);
        long priorFollowUps = appointments.countByRootAppointmentIdAndChildKind(
                followUp.getRootAppointmentId(), AppointmentChildKind.FOLLOW_UP);
        followUp.assignNumber(numbers.childNumber(
                source.getAppointmentNumber(), AppointmentChildKind.FOLLOW_UP, priorFollowUps));
    }

    /// Notification payload for an appointment — IDs only (CLAUDE.md Hard Rule #4). The
    /// human-friendly {@code appointmentNumber} (a business identifier, not PHI) is included when
    /// present so the client can show it without a fetch; legacy rows without one fall back to a
    /// generic message client-side.
    private static Map<String, String> payload(Appointment appt) {
        return appt.getAppointmentNumber() == null
                ? Map.of("appointmentId", appt.getId().toString())
                : Map.of(
                        "appointmentId", appt.getId().toString(),
                        "appointmentNumber", appt.getAppointmentNumber());
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

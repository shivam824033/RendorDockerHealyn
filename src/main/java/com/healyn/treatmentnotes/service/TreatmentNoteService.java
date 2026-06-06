package com.healyn.treatmentnotes.service;

import com.healyn.appointments.domain.Appointment;
import com.healyn.appointments.domain.AppointmentStatus;
import com.healyn.appointments.repository.AppointmentRepository;
import com.healyn.audit.domain.AuditAction;
import com.healyn.audit.domain.AuditResource;
import com.healyn.audit.service.AuditLogger;
import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ConflictException;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnprocessableException;
import com.healyn.common.id.UuidV7;
import com.healyn.common.pagination.Cursor;
import com.healyn.common.pagination.CursorPage;
import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.service.NotificationPublisher;
import com.healyn.treatmentnotes.domain.TreatmentNote;
import com.healyn.treatmentnotes.policy.TreatmentNoteAccessPolicy;
import com.healyn.treatmentnotes.repository.TreatmentNoteRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TreatmentNoteService {

    static final int MAX_FIELD_LENGTH = 8000;

    private final TreatmentNoteRepository notes;
    private final AppointmentRepository appointments;
    private final TreatmentNoteAccessPolicy access;
    private final NotificationPublisher notifications;
    private final AuditLogger audit;

    public TreatmentNoteService(TreatmentNoteRepository notes,
                                AppointmentRepository appointments,
                                TreatmentNoteAccessPolicy access,
                                NotificationPublisher notifications,
                                AuditLogger audit) {
        this.notes = notes;
        this.appointments = appointments;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** Create or replace the single treatment note for a completed appointment. Physio only. */
    @Transactional
    public TreatmentNote upsert(UUID actorId, AccountRole role, UUID appointmentId,
                                UpsertTreatmentNoteRequest req) {
        access.requireWrite(role);
        Appointment appt = loadAppointment(appointmentId);
        if (appt.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ConflictException(ErrorCode.TREATMENT_NOTE_APPOINTMENT_NOT_COMPLETED,
                    "Treatment notes can only be written for COMPLETED appointments");
        }
        validate(req);

        Optional<TreatmentNote> existing = notes.findByAppointmentIdAndDeletedAtIsNull(appointmentId);
        TreatmentNote note;
        AuditAction auditAction;
        if (existing.isPresent()) {
            note = existing.get();
            note.revise(req.diagnosis(), req.notes(), req.recoveryInstructions(), req.nextReviewAt());
            auditAction = AuditAction.UPDATE;
        } else {
            note = notes.save(new TreatmentNote(
                    UuidV7.generate(),
                    appointmentId,
                    appt.getPatientId(),
                    actorId,
                    req.diagnosis(),
                    req.notes(),
                    req.recoveryInstructions(),
                    req.nextReviewAt()));
            auditAction = AuditAction.CREATE;
        }
        notifications.enqueueToPatientManagers(NotificationKind.TREATMENT_NOTE_ADDED, appt.getPatientId(),
                Map.of("appointmentId", appointmentId.toString(), "noteId", note.getId().toString()), note.getId());
        audit.record(auditAction, actorId, role, AuditResource.TREATMENT_NOTE, note.getId(),
                Map.of("appointmentId", appointmentId.toString(), "patientId", appt.getPatientId().toString()));
        return note;
    }

    @Transactional(readOnly = true)
    public TreatmentNote getForAppointment(UUID actorId, AccountRole role, UUID appointmentId) {
        Appointment appt = loadAppointment(appointmentId);
        access.requireRead(actorId, role, appt.getPatientId());
        return notes.findByAppointmentIdAndDeletedAtIsNull(appointmentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TREATMENT_NOTE_NOT_FOUND,
                        "No treatment note for this appointment"));
    }

    @Transactional(readOnly = true)
    public CursorPage<TreatmentNote> listForPatient(UUID actorId, AccountRole role, UUID patientId,
                                                    String cursorToken, int limit) {
        access.requireRead(actorId, role, patientId);
        if (limit <= 0 || limit > 50) limit = 20;

        Limit lim = Limit.of(limit + 1);
        List<TreatmentNote> rows;
        if (cursorToken == null || cursorToken.isBlank()) {
            rows = notes.listFirstPage(patientId, lim);
        } else {
            Cursor c = Cursor.decode(cursorToken);
            rows = notes.listAfterCursor(patientId, c.pivot(), c.id(), lim);
        }

        String nextCursor = null;
        if (rows.size() > limit) {
            TreatmentNote pivot = rows.get(limit - 1);
            nextCursor = new Cursor(pivot.getCreatedAt(), pivot.getId()).encode();
            rows = rows.subList(0, limit);
        }
        return new CursorPage<>(new ArrayList<>(rows), nextCursor);
    }

    // ---- helpers ----

    private Appointment loadAppointment(UUID id) {
        return appointments.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.APPOINTMENT_NOT_FOUND,
                        "Appointment not found"));
    }

    private void validate(UpsertTreatmentNoteRequest req) {
        boolean allBlank = isBlank(req.diagnosis())
                && isBlank(req.notes())
                && isBlank(req.recoveryInstructions());
        if (allBlank) {
            throw new UnprocessableException(ErrorCode.TREATMENT_NOTE_EMPTY,
                    "At least one of diagnosis, notes, or recoveryInstructions is required");
        }
        requireWithinLimit(req.diagnosis());
        requireWithinLimit(req.notes());
        requireWithinLimit(req.recoveryInstructions());
    }

    private void requireWithinLimit(String value) {
        if (value != null && value.length() > MAX_FIELD_LENGTH) {
            throw new UnprocessableException(ErrorCode.TREATMENT_NOTE_FIELD_TOO_LONG,
                    "A treatment note field exceeds " + MAX_FIELD_LENGTH + " characters");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

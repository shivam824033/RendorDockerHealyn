package com.healyn.files.service;

import com.healyn.appointments.domain.Appointment;
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
import com.healyn.files.config.HealynS3Properties;
import com.healyn.files.domain.FileMime;
import com.healyn.files.domain.FileObject;
import com.healyn.files.domain.FileStatus;
import com.healyn.files.policy.FileAccessPolicy;
import com.healyn.files.port.FileReferenceGuard;
import com.healyn.files.port.FileStorePort;
import com.healyn.files.repository.FileObjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class FileService {

    private static final int MAGIC_PROBE_BYTES = 8 * 1024;
    private static final int DAILY_FILE_CAP = 100;
    private static final Duration DAILY_WINDOW = Duration.ofHours(24);

    private final FileObjectRepository files;
    private final AppointmentRepository appointments;
    private final FileAccessPolicy access;
    private final FileStorePort store;
    private final FileReferenceGuard referenceGuard;
    private final AuditLogger audit;
    private final FileQuarantineService quarantineService;
    private final Duration presignTtl;
    private final Clock clock;

    public FileService(FileObjectRepository files,
                       AppointmentRepository appointments,
                       FileAccessPolicy access,
                       FileStorePort store,
                       FileReferenceGuard referenceGuard,
                       AuditLogger audit,
                       FileQuarantineService quarantineService,
                       HealynS3Properties s3,
                       Clock clock) {
        this.files = files;
        this.appointments = appointments;
        this.access = access;
        this.store = store;
        this.referenceGuard = referenceGuard;
        this.audit = audit;
        this.quarantineService = quarantineService;
        this.presignTtl = Duration.ofSeconds(s3.presignTtlSeconds());
        this.clock = clock;
    }

    @Transactional
    public PresignResult presign(UUID actorId, AccountRole role, PresignFileRequest req) {
        FileMime mime = FileMime.fromMimeType(req.mimeType())
                .orElseThrow(() -> new UnprocessableException(ErrorCode.FILE_UNSUPPORTED_MIME,
                        "Unsupported MIME type; allowed: application/pdf, image/jpeg, image/png"));
        if (req.sizeBytes() <= 0 || req.sizeBytes() > mime.maxBytes()) {
            throw new UnprocessableException(ErrorCode.FILE_TOO_LARGE,
                    "size_bytes must be between 1 and " + mime.maxBytes() + " for " + mime.mimeType());
        }
        if (req.kind() == null) {
            throw new UnprocessableException(ErrorCode.FILE_KIND_REQUIRED, "kind is required");
        }
        String filename = FileValidation.sanitizeFilename(req.originalFilename());
        if (!FileValidation.isUsableFilename(filename)) {
            throw new UnprocessableException(ErrorCode.FILE_FILENAME_INVALID, "original_filename is invalid");
        }

        access.requireWrite(actorId, role, req.patientId());
        requireAppointmentForPatient(req.appointmentId(), req.patientId());

        if (files.countByOwnerSince(actorId, Instant.now(clock).minus(DAILY_WINDOW)) >= DAILY_FILE_CAP) {
            throw new ConflictException(ErrorCode.FILE_DAILY_CAP_EXCEEDED,
                    "Daily upload cap of " + DAILY_FILE_CAP + " files reached");
        }

        UUID fileId = UuidV7.generate();
        String storageKey = "patients/%s/appointments/%s/%s.%s".formatted(
                req.patientId(), req.appointmentId(), fileId, mime.extension());

        FileObject file = files.save(new FileObject(
                fileId, actorId, req.patientId(), req.kind(),
                mime.mimeType(), filename, storageKey, req.sizeBytes()));

        String url = store.presignPut(storageKey, mime.mimeType(), presignTtl);
        return new PresignResult(file, url, mime.mimeType(), presignTtl.toSeconds());
    }

    /** Client signals upload completion; server verifies presence, size, and magic bytes. */
    @Transactional
    public FileObject complete(UUID actorId, AccountRole role, UUID fileId) {
        FileObject file = loadActive(fileId);
        access.requireWrite(actorId, role, file.getPatientId());
        if (file.getStatus() != FileStatus.PENDING_UPLOAD) {
            throw new ConflictException(ErrorCode.FILE_INVALID_STATE,
                    "File is not awaiting upload (status=" + file.getStatus() + ")");
        }

        long actualSize = store.objectSize(file.getStorageKey())
                .orElseThrow(() -> new ConflictException(ErrorCode.FILE_OBJECT_MISSING,
                        "No uploaded object found for this file"));
        if (actualSize != file.getSizeBytes()) {
            // Commit the quarantine in its own transaction; throwing rolls back this one.
            quarantineService.quarantine(fileId);
            throw new UnprocessableException(ErrorCode.FILE_MAGIC_BYTE_MISMATCH,
                    "Uploaded size does not match the declared size");
        }

        byte[] head = store.read(file.getStorageKey(), MAGIC_PROBE_BYTES);
        FileMime mime = FileMime.fromMimeType(file.getMimeType()).orElseThrow();
        if (!mime.matchesMagic(head)) {
            quarantineService.quarantine(fileId);
            throw new UnprocessableException(ErrorCode.FILE_MAGIC_BYTE_MISMATCH,
                    "File content does not match its declared type");
        }

        byte[] full = store.read(file.getStorageKey(), file.getSizeBytes());
        file.markAvailable(Instant.now(clock), sha256Hex(full));
        return file;
    }

    @Transactional(readOnly = true)
    public DownloadResult download(UUID actorId, AccountRole role, UUID fileId) {
        FileObject file = loadActive(fileId);
        access.requireRead(actorId, role, file.getPatientId());
        if (file.getStatus() != FileStatus.AVAILABLE) {
            throw new ConflictException(ErrorCode.FILE_INVALID_STATE,
                    "File is not available for download (status=" + file.getStatus() + ")");
        }
        String url = store.presignGet(file.getStorageKey(), file.getOriginalFilename(), presignTtl);
        audit.record(AuditAction.DOWNLOAD, actorId, role, AuditResource.FILE, fileId,
                Map.of("patientId", file.getPatientId().toString()));
        return new DownloadResult(url, presignTtl.toSeconds());
    }

    @Transactional(readOnly = true)
    public FileObject get(UUID actorId, AccountRole role, UUID fileId) {
        FileObject file = loadActive(fileId);
        access.requireRead(actorId, role, file.getPatientId());
        return file;
    }

    @Transactional
    public void delete(UUID actorId, AccountRole role, UUID fileId) {
        FileObject file = loadActive(fileId);
        access.requireWrite(actorId, role, file.getPatientId());
        // Deletion is blocked while any message references the file (FILE_STORAGE_GUIDELINES §10).
        if (referenceGuard.isReferenced(fileId)) {
            throw new ConflictException(ErrorCode.FILE_REFERENCED,
                    "File is referenced by a discussion message and cannot be deleted");
        }
        file.softDelete(Instant.now(clock));
        audit.record(AuditAction.SOFT_DELETE, actorId, role, AuditResource.FILE, fileId,
                Map.of("patientId", file.getPatientId().toString()));
        // TODO move S3 object to cold storage (tombstone).
    }

    // ---- helpers ----

    private FileObject loadActive(UUID id) {
        return files.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.FILE_NOT_FOUND, "File not found"));
    }

    private void requireAppointmentForPatient(UUID appointmentId, UUID patientId) {
        if (appointmentId == null) {
            throw new UnprocessableException(ErrorCode.FILE_APPOINTMENT_REQUIRED,
                    "appointment_id is required in Phase 1");
        }
        Appointment appt = appointments.findByIdAndDeletedAtIsNull(appointmentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.APPOINTMENT_NOT_FOUND, "Appointment not found"));
        if (!appt.getPatientId().equals(patientId)) {
            throw new UnprocessableException(ErrorCode.FILE_PATIENT_MISMATCH,
                    "appointment does not belong to the given patient");
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

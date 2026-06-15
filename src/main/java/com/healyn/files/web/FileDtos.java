package com.healyn.files.web;

import com.healyn.auth.domain.AccountRole;
import com.healyn.files.domain.FileContext;
import com.healyn.files.domain.FileKind;
import com.healyn.files.domain.FileStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FileDtos {

    private FileDtos() {}

    /** The uploader filter for the document listing, mapped to the owning account role. */
    public enum DocumentUploader {
        PATIENT(AccountRole.ROLE_ACCOUNT),
        PHYSIO(AccountRole.ROLE_PHYSIO);

        private final AccountRole role;

        DocumentUploader(AccountRole role) {
            this.role = role;
        }

        public AccountRole role() {
            return role;
        }
    }

    public record PresignBody(
            UUID patientId,
            UUID appointmentId,
            FileKind kind,
            FileContext context,
            String uploadSource,
            String mimeType,
            long sizeBytes,
            String originalFilename) {}

    public record UploadView(String method, String url, Map<String, String> headers, long expiresInSeconds) {}

    public record PresignView(UUID fileId, UploadView upload) {}

    public record DownloadView(String url, long expiresInSeconds) {}

    public record FileView(
            UUID id,
            UUID patientId,
            UUID ownerAccountId,
            FileKind kind,
            String mimeType,
            String originalFilename,
            long sizeBytes,
            FileStatus status,
            Instant createdAt,
            Instant availableAt) {}

    /**
     * A patient's library document for the per-patient listing. {@code uploadedByRole}
     * drives the patient/physio split; {@code appointmentNumber} is the human-friendly
     * id of the linked appointment (null for standalone uploads). {@code originalFilename}
     * is PHI — never log it.
     */
    public record FileDocumentView(
            UUID id,
            UUID patientId,
            FileKind kind,
            String mimeType,
            String originalFilename,
            long sizeBytes,
            AccountRole uploadedByRole,
            UUID appointmentId,
            String appointmentNumber,
            Instant createdAt) {}

    public record DocumentPage(List<FileDocumentView> items, String nextCursor) {}
}

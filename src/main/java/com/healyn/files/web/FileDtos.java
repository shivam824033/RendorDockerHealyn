package com.healyn.files.web;

import com.healyn.files.domain.FileKind;
import com.healyn.files.domain.FileStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class FileDtos {

    private FileDtos() {}

    public record PresignBody(
            UUID patientId,
            UUID appointmentId,
            FileKind kind,
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
}

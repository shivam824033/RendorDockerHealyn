package com.healyn.files.service;

import com.healyn.files.domain.FileContext;
import com.healyn.files.domain.FileKind;

import java.util.UUID;

/**
 * A request to reserve a file row and presign its upload. {@code appointmentId}
 * is optional — null means a standalone library document. {@code context}
 * defaults to {@link FileContext#LIBRARY} when omitted; the discussion composer
 * passes {@link FileContext#DISCUSSION}.
 */
public record PresignFileRequest(
        UUID patientId,
        UUID appointmentId,
        FileKind kind,
        FileContext context,
        String uploadSource,
        String mimeType,
        long sizeBytes,
        String originalFilename) {
}

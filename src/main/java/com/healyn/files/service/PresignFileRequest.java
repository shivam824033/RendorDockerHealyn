package com.healyn.files.service;

import com.healyn.files.domain.FileKind;

import java.util.UUID;

public record PresignFileRequest(
        UUID patientId,
        UUID appointmentId,
        FileKind kind,
        String mimeType,
        long sizeBytes,
        String originalFilename) {
}

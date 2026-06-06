package com.healyn.files.web;

import com.healyn.files.domain.FileObject;
import com.healyn.files.service.PresignResult;

import java.util.Map;

public final class FileMapper {

    private FileMapper() {}

    public static FileDtos.FileView toView(FileObject f) {
        return new FileDtos.FileView(
                f.getId(),
                f.getPatientId(),
                f.getOwnerAccountId(),
                f.getKind(),
                f.getMimeType(),
                f.getOriginalFilename(),
                f.getSizeBytes(),
                f.getStatus(),
                f.getCreatedAt(),
                f.getAvailableAt());
    }

    public static FileDtos.PresignView toPresignView(PresignResult r) {
        FileDtos.UploadView upload = new FileDtos.UploadView(
                "PUT",
                r.uploadUrl(),
                Map.of("Content-Type", r.contentType()),
                r.expiresInSeconds());
        return new FileDtos.PresignView(r.file().getId(), upload);
    }
}

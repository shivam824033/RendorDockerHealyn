package com.healyn.files.service;

import com.healyn.files.domain.FileObject;

public record PresignResult(FileObject file, String uploadUrl, String contentType, long expiresInSeconds) {
}

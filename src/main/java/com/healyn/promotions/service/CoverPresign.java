package com.healyn.promotions.service;

/// The presigned-PUT instruction for a promotion cover upload. The client PUTs the bytes
/// to [url] with the given [contentType], then calls confirm with [objectKey].
public record CoverPresign(String objectKey, String url, String contentType, long expiresInSeconds) {
}

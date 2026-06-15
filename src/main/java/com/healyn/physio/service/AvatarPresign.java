package com.healyn.physio.service;

/// The presigned-PUT instruction for an avatar upload. The client PUTs the bytes
/// to [url] with the given [contentType], then calls confirm with [objectKey].
public record AvatarPresign(String objectKey, String url, String contentType, long expiresInSeconds) {
}

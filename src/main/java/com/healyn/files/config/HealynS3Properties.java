package com.healyn.files.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "healyn.s3")
public record HealynS3Properties(
        String endpoint,
        String publicEndpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        long presignTtlSeconds) {

    /**
     * The endpoint whose host must appear in presigned URLs — the one the client
     * (mobile device / browser) actually connects to. SigV4 signs the host header,
     * so a presigned URL cannot be host-rewritten after signing; it must be minted
     * against the reachable host directly. Falls back to {@link #endpoint()} when
     * no separate public endpoint is configured (the common same-host case).
     */
    public String presignEndpoint() {
        return (publicEndpoint == null || publicEndpoint.isBlank()) ? endpoint : publicEndpoint;
    }
}

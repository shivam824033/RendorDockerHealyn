package com.healyn.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtBlacklist {

    private static final String JTI_PREFIX = "auth:jti:revoked:";
    private static final String SID_PREFIX = "auth:sid:revoked:";

    private final StringRedisTemplate redis;

    public JwtBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) return;
        redis.opsForValue().set(JTI_PREFIX + jti, "1", ttl);
    }

    public boolean isRevoked(String jti) {
        Boolean has = redis.hasKey(JTI_PREFIX + jti);
        return Boolean.TRUE.equals(has);
    }

    /// Revokes every still-live access token bound to a device session (by its
    /// {@code sid} claim). The key only needs to outlive the longest-lived access
    /// token that could have been minted for the session — once revoked, no new
    /// ones are issued (refresh fails) — so {@code ttl} is the access-token TTL.
    public void revokeSession(UUID sessionId, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) return;
        redis.opsForValue().set(SID_PREFIX + sessionId, "1", ttl);
    }

    public boolean isSessionRevoked(String sessionId) {
        Boolean has = redis.hasKey(SID_PREFIX + sessionId);
        return Boolean.TRUE.equals(has);
    }
}

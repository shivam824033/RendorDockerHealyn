package com.healyn.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class JwtBlacklist {

    private static final String KEY_PREFIX = "auth:jti:revoked:";

    private final StringRedisTemplate redis;

    public JwtBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) return;
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isRevoked(String jti) {
        Boolean has = redis.hasKey(KEY_PREFIX + jti);
        return Boolean.TRUE.equals(has);
    }
}

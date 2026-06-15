package com.healyn.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/// Fixed-window counter backed by the same Redis the JWT blacklist uses (no new dependency).
/// {@link #tryAcquire} atomically increments a per-key counter, stamps a TTL on the first hit
/// of a window, and reports whether the caller is still within budget. Fixed windows are cheap
/// and good enough for abuse throttling; the per-account lockout and per-target OTP cap remain
/// the precise controls. Fails open: if Redis is unreachable the request is allowed rather than
/// locking every client out of authentication.
@Component
public class RateLimiter {

    private static final String PREFIX = "auth:rl:";

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /// Returns true if the request is within budget, false if the limit for this window is exceeded.
    public boolean tryAcquire(String bucket, String dimension, int maxRequests, Duration window) {
        String key = PREFIX + bucket + ":" + dimension;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                redis.expire(key, window);
            }
            return count <= maxRequests;
        } catch (RuntimeException e) {
            return true;
        }
    }
}

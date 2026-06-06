package com.healyn.appointments.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class IdempotencyGuard {

    private static final String KEY_PREFIX = "idempotency:appt:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public IdempotencyGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<UUID> lookup(UUID accountId, String key) {
        String value = redis.opsForValue().get(redisKey(accountId, key));
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    public void store(UUID accountId, String key, UUID appointmentId) {
        redis.opsForValue().setIfAbsent(redisKey(accountId, key), appointmentId.toString(), TTL);
    }

    private static String redisKey(UUID accountId, String key) {
        return KEY_PREFIX + accountId + ":" + key;
    }
}

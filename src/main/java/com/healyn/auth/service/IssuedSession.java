package com.healyn.auth.service;

import java.time.Instant;
import java.util.UUID;

public record IssuedSession(
        UUID sessionId,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {
}

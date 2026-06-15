package com.healyn.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

public final class AuthProperties {

    private AuthProperties() {}

    @ConfigurationProperties(prefix = "healyn.jwt")
    public record Jwt(
            String issuer,
            String audience,
            long accessTokenTtlSeconds,
            long refreshTokenTtlDays,
            String privateKeyPath,
            String publicKeyPath) {}

    @ConfigurationProperties(prefix = "healyn.password")
    public record Password(String pepper) {}

    /// Network-level abuse protection for the unauthenticated auth surface (audit H1/H2/M4/M5).
    /// Per-IP fixed-window limits complement the existing per-account lockout (LoginService) and
    /// per-target OTP cap (OtpService): the former blunts password-spraying across many accounts,
    /// the latter stops a single account/inbox being hammered. {@code maxBodyBytes} bounds the
    /// request payload on these endpoints. Disabled in the test profile (shared Redis, fixed IP).
    @ConfigurationProperties(prefix = "healyn.ratelimit")
    public record RateLimit(
            boolean enabled,
            long maxBodyBytes,
            Rule login,
            Rule refresh,
            Rule otpStart) {

        public record Rule(int maxRequests, long windowSeconds) {}
    }
}

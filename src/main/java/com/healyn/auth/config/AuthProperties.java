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
}

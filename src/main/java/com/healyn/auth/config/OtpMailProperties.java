package com.healyn.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Sender identity and subject for OTP emails. SMTP transport is configured via {@code spring.mail.*}. */
@ConfigurationProperties(prefix = "healyn.otp.email")
public record OtpMailProperties(String from, String subject) {}

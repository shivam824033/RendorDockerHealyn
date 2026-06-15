package com.healyn.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healyn.auth.adapter.OtpSender;
import com.healyn.auth.domain.OtpChannel;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Verifies the per-IP auth rate limiter and request-body cap (audit H1/H2/M4/M5). Rate limiting
/// is OFF in the base test profile (shared Redis, one client IP); this class re-enables it with
/// tight limits and drives requests from controlled client addresses.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(AuthRateLimitIntegrationTest.NoopOtpConfig.class)
class AuthRateLimitIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getFirstMappedPort());
        r.add("healyn.password.pepper", () -> "test-pepper-not-a-real-secret");
        // Enable the limiter with tight, deterministic limits for this class only.
        r.add("healyn.ratelimit.enabled", () -> "true");
        r.add("healyn.ratelimit.login.max-requests", () -> "3");
        r.add("healyn.ratelimit.login.window-seconds", () -> "60");
        r.add("healyn.ratelimit.otp-start.max-requests", () -> "2");
        r.add("healyn.ratelimit.otp-start.window-seconds", () -> "600");
    }

    @TestConfiguration
    static class NoopOtpConfig {
        @Bean
        @Primary
        OtpSender noopOtpSender() {
            return (target, channel, code) -> { };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void login_is_rate_limited_per_ip_after_the_budget_is_exhausted() throws Exception {
        byte[] loginBody = json.writeValueAsBytes(Map.of(
                "email_or_phone", "nobody@example.com",
                "password", "whatever-password",
                "device", Map.of("device_id", "dev-1", "device_label", "Test")));

        // 3 attempts from this IP are within budget (each a normal 401).
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/auth/login").with(ip("10.0.0.1"))
                            .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                    .andExpect(status().isUnauthorized());
        }
        // The 4th from the same IP is throttled.
        mvc.perform(post("/auth/login").with(ip("10.0.0.1"))
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isTooManyRequests());

        // A different IP has its own budget — the limit is per-IP, not global.
        mvc.perform(post("/auth/login").with(ip("10.0.0.2"))
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otp_start_is_rate_limited_per_ip_across_distinct_targets() throws Exception {
        // Distinct targets (so the per-target OTP cap does not fire) but the same IP: the per-IP
        // limiter (max 2) blocks the 3rd, proving signup/reset abuse can't be spread across inboxes.
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/auth/register/start").with(ip("10.0.1.1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsBytes(Map.of(
                                    "target", Map.of("email", "u" + UUID.randomUUID() + "@example.com")))))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/auth/register/start").with(ip("10.0.1.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "target", Map.of("email", "u" + UUID.randomUUID() + "@example.com")))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void oversized_request_body_is_rejected_with_413() throws Exception {
        // A body beyond healyn.ratelimit.max-body-bytes (default 64 KiB) is refused before parsing.
        String huge = "x".repeat(70_000);
        byte[] body = json.writeValueAsBytes(Map.of(
                "email_or_phone", "a@example.com",
                "password", huge,
                "device", Map.of("device_id", "dev-1", "device_label", "Test")));

        mvc.perform(post("/auth/login").with(ip("10.0.2.1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge());
    }

    private static RequestPostProcessor ip(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }
}

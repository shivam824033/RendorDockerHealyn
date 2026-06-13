package com.healyn.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healyn.auth.adapter.OtpSender;
import com.healyn.auth.domain.OtpChannel;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(AuthIntegrationTest.CapturingConfig.class)
class AuthIntegrationTest {

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
    }

    @TestConfiguration
    static class CapturingConfig {
        @Bean
        @Primary
        CapturingOtpSender capturingOtpSender() {
            return new CapturingOtpSender();
        }
    }

    static class CapturingOtpSender implements OtpSender {
        final Map<String, String> latestByTarget = new ConcurrentHashMap<>();

        @Override
        public void send(String target, OtpChannel channel, String code) {
            latestByTarget.put(target, code);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturingOtpSender otpSender;

    @BeforeEach
    void resetOtps() {
        otpSender.latestByTarget.clear();
    }

    @Test
    void register_login_refresh_list_revoke_happy_path() throws Exception {
        String email = "alice+" + UUID.randomUUID() + "@example.com";
        Map<String, Object> tokens = registerAndComplete(email, "correct-horse-battery");

        Map<String, Object> loginTokens = body(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "email_or_phone", email,
                                "password", "correct-horse-battery",
                                "device", deviceBody()))))
                .andExpect(status().isOk())
                .andReturn());

        Map<String, Object> refreshed = body(mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("refresh_token", loginTokens.get("refresh_token")))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(refreshed.get("refresh_token")).isNotEqualTo(loginTokens.get("refresh_token"));

        String access = (String) refreshed.get("access_token");
        mvc.perform(get("/auth/sessions").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.sessions[0].device_id").value("dev-1"));

        String sessionId = (String) refreshed.get("session_id");
        mvc.perform(delete("/auth/sessions/" + sessionId).header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
    }

    @Test
    void signing_out_a_device_immediately_invalidates_its_access_token() throws Exception {
        String email = "grace+" + UUID.randomUUID() + "@example.com";
        registerAndComplete(email, "valid-password-7");

        // Device A and device B both sign in for the same account.
        Map<String, Object> deviceA = login(email, "valid-password-7", "dev-A", "Phone A");
        Map<String, Object> deviceB = login(email, "valid-password-7", "dev-B", "Phone B");
        String accessA = (String) deviceA.get("access_token");
        String accessB = (String) deviceB.get("access_token");

        // Both can call an authenticated endpoint to start with.
        mvc.perform(get("/auth/sessions").header("Authorization", "Bearer " + accessA))
                .andExpect(status().isOk());
        mvc.perform(get("/auth/sessions").header("Authorization", "Bearer " + accessB))
                .andExpect(status().isOk());

        // Device A signs device B out of the "Signed-in devices" list.
        mvc.perform(delete("/auth/sessions/" + deviceB.get("session_id"))
                        .header("Authorization", "Bearer " + accessA))
                .andExpect(status().isNoContent());

        // Device B's *access token* is now rejected (not just its refresh token) — the
        // crux of issue 2: revocation is no longer cosmetic.
        mvc.perform(get("/auth/sessions").header("Authorization", "Bearer " + accessB))
                .andExpect(status().isUnauthorized());

        // Device B cannot recover by refreshing either — its session is gone.
        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("refresh_token", deviceB.get("refresh_token")))))
                .andExpect(status().isUnauthorized());

        // ...and device A is untouched: signing out one device must not sign out the others.
        mvc.perform(get("/auth/sessions").header("Authorization", "Bearer " + accessA))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("refresh_token", deviceA.get("refresh_token")))))
                .andExpect(status().isOk());
    }

    @Test
    void five_failed_logins_lock_the_account() throws Exception {
        String email = "bob+" + UUID.randomUUID() + "@example.com";
        registerAndComplete(email, "valid-password-1");

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsBytes(Map.of(
                                    "email_or_phone", email,
                                    "password", "wrong-password",
                                    "device", deviceBody()))))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "email_or_phone", email,
                                "password", "valid-password-1",
                                "device", deviceBody()))))
                .andExpect(status().isLocked());
    }

    @Test
    void refresh_token_replay_revokes_all_sessions_for_account() throws Exception {
        String email = "carol+" + UUID.randomUUID() + "@example.com";
        Map<String, Object> tokens = registerAndComplete(email, "valid-password-2");
        String originalRefresh = (String) tokens.get("refresh_token");

        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("refresh_token", originalRefresh))))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("refresh_token", originalRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otp_rate_limit_returns_429_on_fourth_request_within_window() throws Exception {
        String email = "dave+" + UUID.randomUUID() + "@example.com";
        Map<String, Object> targetBody = Map.of("target", Map.of("email", email));

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/auth/register/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsBytes(targetBody)))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/auth/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(targetBody)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void register_complete_without_valid_otp_returns_422() throws Exception {
        String email = "erin+" + UUID.randomUUID() + "@example.com";
        Map<String, Object> startResp = body(mvc.perform(post("/auth/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("target", Map.of("email", email)))))
                .andExpect(status().isAccepted())
                .andReturn());

        mvc.perform(post("/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "challenge_id", startResp.get("challenge_id"),
                                "code", "000000",
                                "password", "valid-password-3",
                                "device", deviceBody(),
                                "profile", profileBody(),
                                "address", addressBody()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void register_complete_without_address_returns_400() throws Exception {
        String email = "noaddr+" + UUID.randomUUID() + "@example.com";
        Map<String, Object> startResp = body(mvc.perform(post("/auth/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("target", Map.of("email", email)))))
                .andExpect(status().isAccepted())
                .andReturn());
        String code = otpSender.latestByTarget.get(email);
        assertThat(code).isNotNull();

        Map<String, Object> body = new HashMap<>();
        body.put("challenge_id", startResp.get("challenge_id"));
        body.put("code", code);
        body.put("password", "valid-password-9");
        body.put("device", deviceBody());
        body.put("profile", profileBody());
        // address deliberately omitted — it is required at signup.

        mvc.perform(post("/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_creates_primary_patient_for_account() throws Exception {
        String email = "frank+" + UUID.randomUUID() + "@example.com";
        Map<String, Object> tokens = registerAndComplete(email, "valid-password-4");

        String access = (String) tokens.get("access_token");
        mvc.perform(get("/patients").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients").isArray())
                .andExpect(jsonPath("$.patients.length()").value(1))
                .andExpect(jsonPath("$.patients[0].relationship").value("SELF"))
                .andExpect(jsonPath("$.patients[0].primary").value(true))
                .andExpect(jsonPath("$.patients[0].full_name").value("Test Person"));
    }

    private Map<String, Object> registerAndComplete(String email, String password) throws Exception {
        Map<String, Object> startResp = body(mvc.perform(post("/auth/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("target", Map.of("email", email)))))
                .andExpect(status().isAccepted())
                .andReturn());
        String code = otpSender.latestByTarget.get(email);
        assertThat(code).as("captured OTP for " + email).isNotNull();

        Map<String, Object> body = new HashMap<>();
        body.put("challenge_id", startResp.get("challenge_id"));
        body.put("code", code);
        body.put("password", password);
        body.put("device", deviceBody());
        body.put("profile", profileBody());
        body.put("address", addressBody());

        return body(mvc.perform(post("/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private Map<String, Object> login(String email, String password, String deviceId, String deviceLabel)
            throws Exception {
        return body(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "email_or_phone", email,
                                "password", password,
                                "device", Map.of("device_id", deviceId, "device_label", deviceLabel)))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static Map<String, Object> deviceBody() {
        return Map.of("device_id", "dev-1", "device_label", "Test Phone");
    }

    private static Map<String, Object> profileBody() {
        return Map.of(
                "full_name", "Test Person",
                "date_of_birth", "1990-01-15",
                "sex", "UNDISCLOSED");
    }

    private static Map<String, Object> addressBody() {
        return Map.of(
                "line1", "1 Test Street",
                "city", "Pune",
                "state", "Maharashtra",
                "postal_code", "411001",
                "country", "India");
    }

    private Map<String, Object> body(MvcResult result) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsByteArray());
        Map<String, Object> map = new HashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(),
                e.getValue().isTextual() ? e.getValue().asText() : json.convertValue(e.getValue(), Object.class)));
        return map;
    }
}

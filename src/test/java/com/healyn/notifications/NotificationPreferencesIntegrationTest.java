package com.healyn.notifications;

import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(NotificationPreferencesIntegrationTest.CapturingConfig.class)
class NotificationPreferencesIntegrationTest {

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

    @Test
    void get_returns_all_enabled_defaults_for_a_fresh_account() throws Exception {
        String access = (String) registerAccount().get("access_token");

        mvc.perform(get("/notifications/preferences").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment_updates").value(true))
                .andExpect(jsonPath("$.appointment_reminders").value(true))
                .andExpect(jsonPath("$.messages").value(true))
                .andExpect(jsonPath("$.treatment_notes").value(true));
    }

    @Test
    void patch_disables_one_category_and_leaves_the_rest_then_persists() throws Exception {
        String access = (String) registerAccount().get("access_token");

        mvc.perform(patch("/notifications/preferences")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("messages", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").value(false))
                .andExpect(jsonPath("$.appointment_updates").value(true));

        // Re-read: the opt-out was persisted and the untouched categories stayed enabled.
        mvc.perform(get("/notifications/preferences").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").value(false))
                .andExpect(jsonPath("$.appointment_updates").value(true))
                .andExpect(jsonPath("$.appointment_reminders").value(true))
                .andExpect(jsonPath("$.treatment_notes").value(true));
    }

    @Test
    void get_without_auth_is_401() throws Exception {
        mvc.perform(get("/notifications/preferences"))
                .andExpect(status().isUnauthorized());
    }

    private Map<String, Object> registerAccount() throws Exception {
        String email = "prefs+" + UUID.randomUUID() + "@example.com";
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
        body.put("password", "valid-password-prefs");
        body.put("device", Map.of("device_id", "dev-1", "device_label", "Test Phone"));
        body.put("profile", Map.of(
                "full_name", "Test Person",
                "date_of_birth", "1990-01-15",
                "sex", "UNDISCLOSED"));

        return body(mvc.perform(post("/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private Map<String, Object> body(MvcResult result) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsByteArray());
        Map<String, Object> map = new HashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(),
                e.getValue().isTextual() ? e.getValue().asText() : json.convertValue(e.getValue(), Object.class)));
        return map;
    }
}

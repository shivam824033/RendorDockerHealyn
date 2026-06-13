package com.healyn.availability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healyn.auth.adapter.OtpSender;
import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.domain.OtpChannel;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.service.AccessTokenIssuer;
import com.healyn.common.id.UuidV7;
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

import java.time.LocalDate;
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
@Import(AvailabilityIntegrationTest.CapturingConfig.class)
class AvailabilityIntegrationTest {

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
    @Autowired AccountRepository accounts;
    @Autowired AccessTokenIssuer tokenIssuer;

    @BeforeEach
    void reset() {
        otpSender.latestByTarget.clear();
    }

    @Test
    void physio_creates_rule_and_blackout_then_patient_sees_correct_slots() throws Exception {
        Session physio = seedPhysio("physio-a");
        Session patient = registerPatient("aria");

        UUID ruleId = createRule(physio, Map.of(
                "day_of_week", 1,
                "start_time", "09:00:00",
                "end_time", "13:00:00",
                "slot_minutes", 30,
                "timezone", "Asia/Kolkata",
                "effective_from", "2026-01-01"));
        assertThat(ruleId).isNotNull();

        mvc.perform(post("/availability/blackouts")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "starts_at", "2026-06-15T10:00:00+05:30",
                                "ends_at",   "2026-06-15T11:00:00+05:30",
                                "reason",   "Personal"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/availability")
                        .param("physiotherapist_id", physio.id.toString())
                        .param("from", "2026-06-15")
                        .param("to",   "2026-06-15")
                        .header("Authorization", "Bearer " + patient.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.physiotherapist_id").value(physio.id.toString()))
                .andExpect(jsonPath("$.slots.length()").value(6))
                .andExpect(jsonPath("$.slots[0].duration_minutes").value(30));
    }

    @Test
    void patient_cannot_create_rule() throws Exception {
        Session patient = registerPatient("nora");

        mvc.perform(post("/availability/rules")
                        .header("Authorization", "Bearer " + patient.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(ruleBody())))
                .andExpect(status().isForbidden());
    }

    @Test
    void overlapping_blackout_returns_409() throws Exception {
        Session physio = seedPhysio("physio-b");

        Map<String, Object> first = Map.of(
                "starts_at", "2026-07-01T09:00:00Z",
                "ends_at",   "2026-07-01T11:00:00Z");
        mvc.perform(post("/availability/blackouts")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(first)))
                .andExpect(status().isCreated());

        Map<String, Object> overlap = Map.of(
                "starts_at", "2026-07-01T10:00:00Z",
                "ends_at",   "2026-07-01T12:00:00Z");
        mvc.perform(post("/availability/blackouts")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(overlap)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("availability.blackout_overlap"));
    }

    @Test
    void archiving_rule_excludes_future_dates_from_slot_listing() throws Exception {
        Session physio = seedPhysio("physio-c");

        UUID ruleId = createRule(physio, ruleBody());

        mvc.perform(delete("/availability/rules/" + ruleId)
                        .header("Authorization", "Bearer " + physio.access))
                .andExpect(status().isNoContent());

        LocalDate far = LocalDate.now().plusDays(20);
        LocalDate sameWeekMonday = far.with(java.time.DayOfWeek.MONDAY);
        mvc.perform(get("/availability")
                        .param("physiotherapist_id", physio.id.toString())
                        .param("from", sameWeekMonday.toString())
                        .param("to",   sameWeekMonday.toString())
                        .header("Authorization", "Bearer " + physio.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(0));

        mvc.perform(get("/availability/rules")
                        .header("Authorization", "Bearer " + physio.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].effective_to").isNotEmpty());
    }

    @Test
    void invalid_timezone_returns_422() throws Exception {
        Session physio = seedPhysio("physio-d");

        Map<String, Object> bad = new HashMap<>(ruleBody());
        bad.put("timezone", "Mars/Phobos");

        mvc.perform(post("/availability/rules")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("availability.invalid_timezone"));
    }

    @Test
    void slot_range_over_31_days_returns_422() throws Exception {
        Session physio = seedPhysio("physio-e");

        mvc.perform(get("/availability")
                        .param("physiotherapist_id", physio.id.toString())
                        .param("from", "2026-06-01")
                        .param("to",   "2026-08-01")
                        .header("Authorization", "Bearer " + physio.access))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("availability.invalid_range"));
    }

    private UUID createRule(Session physio, Map<String, Object> body) throws Exception {
        Map<String, Object> resp = body(mvc.perform(post("/availability/rules")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn());
        return UUID.fromString((String) resp.get("id"));
    }

    private static Map<String, Object> ruleBody() {
        return Map.of(
                "day_of_week", 1,
                "start_time", "09:00:00",
                "end_time", "13:00:00",
                "slot_minutes", 30,
                "timezone", "Asia/Kolkata",
                "effective_from", "2026-01-01");
    }

    private Session seedPhysio(String tag) {
        String email = tag + "+" + UUID.randomUUID() + "@clinic.example.com";
        Account physio = new Account(
                UuidV7.generate(), email, null,
                "$argon2id$placeholder$noop", new byte[]{0},
                AccountRole.ROLE_PHYSIO);
        accounts.save(physio);
        String token = tokenIssuer.issue(physio, java.util.UUID.randomUUID()).token();
        return new Session(physio.getId(), token);
    }

    private Session registerPatient(String tag) throws Exception {
        String email = tag + "+" + UUID.randomUUID() + "@example.com";
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
        body.put("password", "valid-password-x");
        body.put("device", Map.of("device_id", "dev-1", "device_label", "Phone"));
        body.put("profile", Map.of(
                "full_name", tag + " Person",
                "date_of_birth", "1991-05-20",
                "sex", "UNDISCLOSED"));
        body.put("address", Map.of(
                "line1", "1 Test Street",
                "city", "Pune",
                "state", "Maharashtra",
                "postal_code", "411001",
                "country", "India"));

        Map<String, Object> tokens = body(mvc.perform(post("/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn());
        return new Session(null, (String) tokens.get("access_token"));
    }

    private Map<String, Object> body(MvcResult result) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsByteArray());
        Map<String, Object> map = new HashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(),
                e.getValue().isTextual() ? e.getValue().asText() : json.convertValue(e.getValue(), Object.class)));
        return map;
    }

    private record Session(UUID id, String access) {}
}

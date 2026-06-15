package com.healyn.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healyn.auth.adapter.OtpSender;
import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountStatus;
import com.healyn.auth.domain.OtpChannel;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.compliance.service.AccountDeletionService;
import com.healyn.patients.domain.Patient;
import com.healyn.patients.repository.PatientRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(ComplianceIntegrationTest.CapturingConfig.class)
class ComplianceIntegrationTest {

    private static final String PASSWORD = "valid-password-x";

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
        // Deterministic erasure: zero grace and a disabled poller so the test drives the sweep.
        r.add("healyn.compliance.grace-days", () -> "0");
        r.add("healyn.compliance.poller-enabled", () -> "false");
    }

    @TestConfiguration
    static class CapturingConfig {
        @Bean @Primary
        CapturingOtpSender capturingOtpSender() {
            return new CapturingOtpSender();
        }
    }

    static class CapturingOtpSender implements OtpSender {
        final Map<String, String> latestByTarget = new ConcurrentHashMap<>();
        @Override public void send(String target, OtpChannel channel, String code) {
            latestByTarget.put(target, code);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturingOtpSender otpSender;
    @Autowired AccountRepository accounts;
    @Autowired PatientRepository patients;
    @Autowired AccountDeletionService deletions;

    @BeforeEach
    void reset() {
        otpSender.latestByTarget.clear();
    }

    @Test
    void privacy_policy_is_publicly_served() throws Exception {
        mvc.perform(get("/legal/privacy_policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("PRIVACY_POLICY"))
                .andExpect(jsonPath("$.version").value("2026-06-14"))
                .andExpect(jsonPath("$.body_markdown").value(org.hamcrest.Matchers.containsString("DRAFT")));
    }

    @Test
    void unknown_legal_document_returns_404() throws Exception {
        mvc.perform(get("/legal/cookie_policy")).andExpect(status().isNotFound());
    }

    @Test
    void registration_records_three_account_consents() throws Exception {
        String access = register("consenter").access;
        mvc.perform(get("/me/consents").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents.length()").value(3));
    }

    @Test
    void family_add_without_authority_is_rejected() throws Exception {
        String access = register("guardian").access;
        String body = json.writeValueAsString(Map.of(
                "full_name", "Kid", "date_of_birth", "2015-01-01", "sex", "MALE",
                "relationship", "CHILD", "authority_attested", false));
        // authority_attested=false fails @AssertTrue bean validation → 400 (matches the
        // codebase's validation-error convention); the service throws 422 as a backstop.
        mvc.perform(post("/patients").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletion_request_then_sweep_anonymizes_account_and_retains_patient_row() throws Exception {
        Session s = register("leaver");
        UUID accountId = accounts.findByEmail(s.email).orElseThrow().getId();
        UUID primaryPatientId = patients.findById(firstPatientIdFor(s.access)).orElseThrow().getId();

        mvc.perform(post("/me/deletion-request").header("Authorization", "Bearer " + s.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", PASSWORD, "reason", "done"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        // Grace is zero, so the request is immediately due. Drive the sweep directly.
        assertThat(deletions.processDueAnonymizations()).isEqualTo(1);

        Account erased = accounts.findById(accountId).orElseThrow();
        // Email is replaced by a non-identifying tombstone (the check needs one contact column);
        // the real address is gone and the phone is cleared.
        assertThat(erased.getEmail()).doesNotContain(s.email).endsWith("@anonymized.invalid");
        assertThat(erased.getPhoneE164()).isNull();
        assertThat(erased.getStatus()).isEqualTo(AccountStatus.DISABLED);
        assertThat(erased.getDeletedAt()).isNotNull();

        // The clinical-scaffold patient row is RETAINED (not hard-deleted) but de-identified.
        Patient patient = patients.findById(primaryPatientId).orElseThrow();
        assertThat(patient.getFullName()).isEqualTo(Patient.REDACTED_NAME);
        assertThat(patient.getEmail()).isNull();
        assertThat(patient.getDeletedAt()).isNotNull();
    }

    private UUID firstPatientIdFor(String access) throws Exception {
        JsonNode node = json.readTree(mvc.perform(get("/patients").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        return UUID.fromString(node.get("patients").get(0).get("id").asText());
    }

    private Session register(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.com";
        Map<String, Object> startResp = body(mvc.perform(post("/auth/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("target", Map.of("email", email)))))
                .andExpect(status().isAccepted()).andReturn());
        String code = otpSender.latestByTarget.get(email);
        assertThat(code).isNotNull();

        Map<String, Object> body = new HashMap<>();
        body.put("challenge_id", startResp.get("challenge_id"));
        body.put("code", code);
        body.put("password", PASSWORD);
        body.put("device", Map.of("device_id", "dev-1", "device_label", "Phone"));
        body.put("profile", Map.of("full_name", prefix + " Person",
                "date_of_birth", "1991-05-20", "sex", "UNDISCLOSED"));
        body.put("consents", Map.of("terms_accepted", true, "privacy_accepted", true,
                "health_data_processing_accepted", true));
        body.put("address", Map.of("line1", "1 Test Street", "city", "Pune",
                "state", "Maharashtra", "postal_code", "411001", "country", "India"));

        Map<String, Object> tokens = body(mvc.perform(post("/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andReturn());
        return new Session((String) tokens.get("access_token"), email);
    }

    private Map<String, Object> body(MvcResult result) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsByteArray());
        Map<String, Object> map = new HashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(),
                e.getValue().isTextual() ? e.getValue().asText() : json.convertValue(e.getValue(), Object.class)));
        return map;
    }

    private record Session(String access, String email) {}
}

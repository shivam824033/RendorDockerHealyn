package com.healyn.promotions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healyn.auth.adapter.OtpSender;
import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.domain.OtpChannel;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.service.AccessTokenIssuer;
import com.healyn.common.id.UuidV7;
import com.healyn.files.port.FileStorePort;
import com.healyn.promotions.repository.PromotionRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(PromotionIntegrationTest.PromotionTestConfig.class)
class PromotionIntegrationTest {

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
        // Small cap so the active-limit guard is exercised cheaply.
        r.add("healyn.promotions.max-active", () -> 2);
    }

    @TestConfiguration
    static class PromotionTestConfig {
        @Bean @Primary CapturingOtpSender capturingOtpSender() { return new CapturingOtpSender(); }
        @Bean @Primary InMemoryFileStore inMemoryFileStore() { return new InMemoryFileStore(); }
    }

    static class CapturingOtpSender implements OtpSender {
        final Map<String, String> latestByTarget = new ConcurrentHashMap<>();
        @Override public void send(String target, OtpChannel channel, String code) {
            latestByTarget.put(target, code);
        }
    }

    static class InMemoryFileStore implements FileStorePort {
        final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        @Override public String presignPut(String key, String contentType, Duration ttl) { return "memory://" + key; }
        @Override public String presignGet(String key, String filename, Duration ttl) { return "memory://" + key + "?dl=" + filename; }
        @Override public Optional<Long> objectSize(String key) {
            byte[] b = objects.get(key);
            return b == null ? Optional.empty() : Optional.of((long) b.length);
        }
        @Override public byte[] read(String key, long maxBytes) {
            byte[] b = objects.getOrDefault(key, new byte[0]);
            int n = (int) Math.min(maxBytes, b.length);
            byte[] out = new byte[n];
            System.arraycopy(b, 0, out, 0, n);
            return out;
        }
        @Override public void delete(String key) { objects.remove(key); }
        void put(String key, byte[] bytes) { objects.put(key, bytes); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturingOtpSender otpSender;
    @Autowired InMemoryFileStore fileStore;
    @Autowired AccountRepository accounts;
    @Autowired AccessTokenIssuer tokenIssuer;
    @Autowired PromotionRepository promotions;

    @BeforeEach
    void reset() {
        otpSender.latestByTarget.clear();
        fileStore.objects.clear();
        // Promotions persist in the shared container; clear them so the active cap and
        // ordering assertions start from a clean slate each test.
        promotions.deleteAll();
    }

    @Test
    void physio_creates_promotion_and_patient_sees_only_safe_fields() throws Exception {
        String physio = seedPhysio();
        create(physio, Map.of(
                "title", "Sports rehab package",
                "short_description", "6-week recovery programme",
                "long_description", "Full guided programme for ACL recovery.",
                "service_category", "Rehabilitation",
                "cta_text", "Book now",
                "cta_action", "BOOK_APPOINTMENT"));

        String patient = registerPatient("amy").access;
        mvc.perform(get("/promotions").header("Authorization", "Bearer " + patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotions[0].title").value("Sports rehab package"))
                .andExpect(jsonPath("$.promotions[0].cta_action").value("BOOK_APPOINTMENT"))
                // internal management fields must not leak to the patient view
                .andExpect(jsonPath("$.promotions[0].active").doesNotExist())
                .andExpect(jsonPath("$.promotions[0].starts_at").doesNotExist());
    }

    @Test
    void patient_cannot_create_a_promotion() throws Exception {
        String patient = registerPatient("ben").access;
        mvc.perform(post("/promotions")
                        .header("Authorization", "Bearer " + patient)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Imposter"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("promotions.forbidden"));
    }

    @Test
    void inactive_and_out_of_window_promotions_are_hidden_from_patients() throws Exception {
        String physio = seedPhysio();
        create(physio, Map.of("title", "Live one"));
        // inactive
        create(physio, Map.of("title", "Hidden one", "active", false));
        // future window
        Instant future = Instant.now().plus(2, ChronoUnit.DAYS);
        create(physio, Map.of("title", "Future one", "starts_at", future.toString()));

        String patient = registerPatient("cara").access;
        mvc.perform(get("/promotions").header("Authorization", "Bearer " + patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotions.length()").value(1))
                .andExpect(jsonPath("$.promotions[0].title").value("Live one"));
    }

    @Test
    void active_cap_is_enforced() throws Exception {
        String physio = seedPhysio();
        create(physio, Map.of("title", "One"));
        create(physio, Map.of("title", "Two"));
        // third active would exceed the configured cap of 2
        mvc.perform(post("/promotions")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Three"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("promotions.limit_reached"));
    }

    @Test
    void reorder_changes_the_patient_facing_order() throws Exception {
        String physio = seedPhysio();
        UUID first = create(physio, Map.of("title", "First"));
        UUID second = create(physio, Map.of("title", "Second"));

        mvc.perform(post("/promotions/reorder")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "ordered_ids", List.of(second.toString(), first.toString())))))
                .andExpect(status().isOk());

        String patient = registerPatient("dan").access;
        mvc.perform(get("/promotions").header("Authorization", "Bearer " + patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotions[0].title").value("Second"))
                .andExpect(jsonPath("$.promotions[1].title").value("First"));
    }

    @Test
    void cover_presign_then_confirm_accepts_webp_and_exposes_a_url() throws Exception {
        String physio = seedPhysio();
        UUID id = create(physio, Map.of("title", "With cover"));

        MvcResult res = mvc.perform(post("/promotions/" + id + "/cover/presign")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mime_type", "image/webp", "size_bytes", 2048))))
                .andExpect(status().isOk())
                .andReturn();
        String key = json.readTree(res.getResponse().getContentAsByteArray()).get("object_key").asText();
        fileStore.put(key, webpBytes(2048));

        mvc.perform(post("/promotions/" + id + "/cover/confirm")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("object_key", key, "mime_type", "image/webp"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cover_url").isNotEmpty());

        String patient = registerPatient("eve").access;
        mvc.perform(get("/promotions").header("Authorization", "Bearer " + patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotions[0].cover_url").isNotEmpty());
    }

    @Test
    void cover_confirm_rejects_wrong_magic_bytes() throws Exception {
        String physio = seedPhysio();
        UUID id = create(physio, Map.of("title", "Bad cover"));
        MvcResult res = mvc.perform(post("/promotions/" + id + "/cover/presign")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mime_type", "image/webp", "size_bytes", 64))))
                .andExpect(status().isOk())
                .andReturn();
        String key = json.readTree(res.getResponse().getContentAsByteArray()).get("object_key").asText();
        fileStore.put(key, new byte[64]); // zero bytes — not a WEBP

        mvc.perform(post("/promotions/" + id + "/cover/confirm")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("object_key", key, "mime_type", "image/webp"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("promotions.cover_invalid"));
    }

    @Test
    void deleted_promotion_disappears_from_patient_view() throws Exception {
        String physio = seedPhysio();
        UUID id = create(physio, Map.of("title", "Temporary"));
        mvc.perform(delete("/promotions/" + id).header("Authorization", "Bearer " + physio))
                .andExpect(status().isNoContent());

        String patient = registerPatient("fay").access;
        mvc.perform(get("/promotions").header("Authorization", "Bearer " + patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotions.length()").value(0));
    }

    // ---- helpers ----

    /** Creates a promotion as the physio and returns its id. */
    private UUID create(String physioToken, Map<String, ?> body) throws Exception {
        MvcResult res = mvc.perform(post("/promotions")
                        .header("Authorization", "Bearer " + physioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = json.readTree(res.getResponse().getContentAsByteArray());
        return UUID.fromString(node.get("id").asText());
    }

    private String seedPhysio() {
        Account physio = new Account(
                UuidV7.generate(), "physio+" + UUID.randomUUID() + "@clinic.example.com", null,
                "$argon2id$placeholder$noop", new byte[]{0}, AccountRole.ROLE_PHYSIO);
        accounts.save(physio);
        return tokenIssuer.issue(physio, UUID.randomUUID()).token();
    }

    private Session registerPatient(String tag) throws Exception {
        String email = tag + "+" + UUID.randomUUID() + "@example.com";
        MvcResult startRes = mvc.perform(post("/auth/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("target", Map.of("email", email)))))
                .andExpect(status().isAccepted())
                .andReturn();
        String challengeId = json.readTree(startRes.getResponse().getContentAsByteArray())
                .get("challenge_id").asText();
        String code = otpSender.latestByTarget.get(email);
        assertThat(code).isNotNull();

        Map<String, Object> body = new HashMap<>();
        body.put("challenge_id", challengeId);
        body.put("code", code);
        body.put("password", "valid-password-x");
        body.put("device", Map.of("device_id", "dev-" + UUID.randomUUID(), "device_label", "Phone"));
        body.put("profile", Map.of(
                "full_name", tag + " Person",
                "date_of_birth", "1991-05-20",
                "sex", "UNDISCLOSED"));
        body.put("consents", Map.of("terms_accepted", true, "privacy_accepted", true, "health_data_processing_accepted", true));
        body.put("address", Map.of(
                "line1", "1 Test Street",
                "city", "Pune",
                "state", "Maharashtra",
                "postal_code", "411001",
                "country", "India"));

        MvcResult tokensRes = mvc.perform(post("/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn();
        String access = json.readTree(tokensRes.getResponse().getContentAsByteArray())
                .get("access_token").asText();
        return new Session(access);
    }

    /** A minimal valid WEBP header: "RIFF" + 4-byte size + "WEBP". */
    private static byte[] webpBytes(int length) {
        byte[] b = new byte[Math.max(length, 12)];
        byte[] riff = {0x52, 0x49, 0x46, 0x46};
        byte[] webp = {0x57, 0x45, 0x42, 0x50};
        System.arraycopy(riff, 0, b, 0, 4);
        System.arraycopy(webp, 0, b, 8, 4);
        return b;
    }

    private record Session(String access) {}
}

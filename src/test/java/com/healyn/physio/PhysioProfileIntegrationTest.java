package com.healyn.physio;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
@Import(PhysioProfileIntegrationTest.PhysioTestConfig.class)
class PhysioProfileIntegrationTest {

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
    static class PhysioTestConfig {
        @Bean @Primary CapturingOtpSender capturingOtpSender() { return new CapturingOtpSender(); }
        @Bean @Primary InMemoryFileStore inMemoryFileStore() { return new InMemoryFileStore(); }
    }

    static class CapturingOtpSender implements OtpSender {
        final Map<String, String> latestByTarget = new ConcurrentHashMap<>();
        @Override public void send(String target, OtpChannel channel, String code) {
            latestByTarget.put(target, code);
        }
    }

    /** In-memory stand-in for S3; tests inject bytes via put() to simulate the client's direct PUT. */
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

    @BeforeEach
    void reset() {
        otpSender.latestByTarget.clear();
        fileStore.objects.clear();
    }

    @Test
    void physio_updates_profile_and_patient_can_read_it() throws Exception {
        String physio = seedPhysio();
        mvc.perform(patch("/physio/profile")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "display_name", "Dr. Asha Rao",
                                "qualification", "MPT (Ortho)",
                                "experience_years", 12,
                                "specialization", "Sports rehab",
                                "clinic_name", "Healyn Physio Clinic",
                                "clinic_contact_phone", "+919876543210",
                                "instagram_url", "https://instagram.com/healyn"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.display_name").value("Dr. Asha Rao"))
                .andExpect(jsonPath("$.experience_years").value(12))
                .andExpect(jsonPath("$.instagram_url").value("https://instagram.com/healyn"));

        String patient = registerPatient("amy").access;
        mvc.perform(get("/physio/profile").header("Authorization", "Bearer " + patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinic_name").value("Healyn Physio Clinic"))
                .andExpect(jsonPath("$.clinic_contact_phone").value("+919876543210"));
    }

    @Test
    void update_rejects_a_malformed_social_url() throws Exception {
        String physio = seedPhysio();
        mvc.perform(patch("/physio/profile")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("instagram_url", "not-a-url"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("common.validation_failed"));
    }

    @Test
    void patient_cannot_update_the_profile() throws Exception {
        Session patient = registerPatient("ben");
        mvc.perform(patch("/physio/profile")
                        .header("Authorization", "Bearer " + patient.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("display_name", "Imposter"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("physio.forbidden"));
    }

    @Test
    void avatar_presign_then_confirm_exposes_a_download_url() throws Exception {
        String physio = seedPhysio();
        String key = presignAvatar(physio, "image/jpeg", 1024);
        fileStore.put(key, jpegBytes(1024));

        mvc.perform(post("/physio/profile/avatar/confirm")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("object_key", key, "mime_type", "image/jpeg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar_url").isNotEmpty());

        String patient = registerPatient("cara").access;
        mvc.perform(get("/physio/profile").header("Authorization", "Bearer " + patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar_url").isNotEmpty());
    }

    @Test
    void avatar_confirm_rejects_wrong_magic_bytes() throws Exception {
        String physio = seedPhysio();
        String key = presignAvatar(physio, "image/jpeg", 512);
        fileStore.put(key, new byte[512]); // zero bytes — not a JPEG

        mvc.perform(post("/physio/profile/avatar/confirm")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("object_key", key, "mime_type", "image/jpeg"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("physio.avatar_invalid"));
    }

    @Test
    void avatar_presign_rejects_a_non_image_type() throws Exception {
        String physio = seedPhysio();
        mvc.perform(post("/physio/profile/avatar/presign")
                        .header("Authorization", "Bearer " + physio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mime_type", "application/pdf", "size_bytes", 1024))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("physio.avatar_unsupported_mime"));
    }

    // ---- helpers ----

    private String presignAvatar(String physioToken, String mime, long size) throws Exception {
        MvcResult res = mvc.perform(post("/physio/profile/avatar/presign")
                        .header("Authorization", "Bearer " + physioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("mime_type", mime, "size_bytes", size))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsByteArray()).get("object_key").asText();
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

    private static byte[] jpegBytes(int length) {
        byte[] b = new byte[length];
        byte[] magic = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        System.arraycopy(magic, 0, b, 0, Math.min(magic.length, length));
        return b;
    }

    private record Session(String access) {}
}

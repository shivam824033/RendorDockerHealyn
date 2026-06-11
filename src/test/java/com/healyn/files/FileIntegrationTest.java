package com.healyn.files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healyn.appointments.domain.Appointment;
import com.healyn.appointments.repository.AppointmentRepository;
import com.healyn.appointments.service.AppointmentNumberGenerator;
import com.healyn.auth.adapter.OtpSender;
import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.domain.OtpChannel;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.service.AccessTokenIssuer;
import com.healyn.common.id.UuidV7;
import com.healyn.files.domain.FileObject;
import com.healyn.files.port.FileStorePort;
import com.healyn.files.repository.FileObjectRepository;
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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
@Import(FileIntegrationTest.FilesTestConfig.class)
class FileIntegrationTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

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
    static class FilesTestConfig {
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
    @Autowired AppointmentRepository appointments;
    @Autowired AppointmentNumberGenerator numbers;
    @Autowired FileObjectRepository files;
    @Autowired AccessTokenIssuer tokenIssuer;

    @BeforeEach
    void reset() {
        otpSender.latestByTarget.clear();
        fileStore.objects.clear();
    }

    @Test
    void presign_then_complete_promotes_to_available() throws Exception {
        Fixture f = bootstrap("anna");
        UUID fileId = presign(f.account, f.patientId, f.apptId, "application/pdf", 1024, "spine-mri.pdf");

        FileObject row = files.findById(fileId).orElseThrow();
        assertThat(row.getStatus().name()).isEqualTo("PENDING_UPLOAD");
        fileStore.put(row.getStorageKey(), pdfBytes(1024));

        mvc.perform(post("/files/" + fileId + "/complete")
                        .header("Authorization", "Bearer " + f.account.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        mvc.perform(get("/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + f.account.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.expires_in_seconds").value(300));
    }

    @Test
    void unsupported_mime_is_rejected_at_presign() throws Exception {
        Fixture f = bootstrap("ben");
        mvc.perform(post("/files/presign")
                        .header("Authorization", "Bearer " + f.account.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(presignBody(
                                f.patientId, f.apptId, "application/zip", 1024, "x.zip"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("files.unsupported_mime"));
    }

    @Test
    void oversize_file_is_rejected_at_presign() throws Exception {
        Fixture f = bootstrap("cara");
        long thirtyMb = 30L * 1024 * 1024;
        mvc.perform(post("/files/presign")
                        .header("Authorization", "Bearer " + f.account.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(presignBody(
                                f.patientId, f.apptId, "application/pdf", thirtyMb, "huge.pdf"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("files.too_large"));
    }

    @Test
    void complete_quarantines_when_magic_bytes_mismatch() throws Exception {
        Fixture f = bootstrap("dan");
        UUID fileId = presign(f.account, f.patientId, f.apptId, "image/jpeg", 512, "fake.jpg");

        FileObject row = files.findById(fileId).orElseThrow();
        byte[] notAJpeg = pdfBytes(512); // correct length, wrong content
        fileStore.put(row.getStorageKey(), notAJpeg);

        mvc.perform(post("/files/" + fileId + "/complete")
                        .header("Authorization", "Bearer " + f.account.access))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("files.magic_byte_mismatch"));

        assertThat(files.findById(fileId).orElseThrow().getStatus().name()).isEqualTo("QUARANTINED");
    }

    @Test
    void presign_for_unowned_patient_is_forbidden() throws Exception {
        Fixture owner = bootstrap("eli");
        Fixture stranger = bootstrap("fae");

        mvc.perform(post("/files/presign")
                        .header("Authorization", "Bearer " + stranger.account.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(presignBody(
                                owner.patientId, owner.apptId, "application/pdf", 1024, "report.pdf"))))
                .andExpect(status().isForbidden());
    }

    // ---- fixtures + helpers ----

    private record Fixture(Session account, UUID patientId, UUID apptId) {}

    private Fixture bootstrap(String tag) throws Exception {
        Session physio = seedPhysio();
        Session account = registerPatient(tag);
        UUID patientId = primaryPatientId(account);
        UUID apptId = seedAppointment(physio, account, patientId);
        return new Fixture(account, patientId, apptId);
    }

    private UUID presign(Session actor, UUID patientId, UUID apptId,
                         String mime, long size, String filename) throws Exception {
        MvcResult res = mvc.perform(post("/files/presign")
                        .header("Authorization", "Bearer " + actor.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(presignBody(patientId, apptId, mime, size, filename))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upload.method").value("PUT"))
                .andReturn();
        return UUID.fromString(json.readTree(res.getResponse().getContentAsByteArray()).get("file_id").asText());
    }

    private static Map<String, Object> presignBody(UUID patientId, UUID apptId,
                                                   String mime, long size, String filename) {
        Map<String, Object> body = new HashMap<>();
        body.put("patient_id", patientId.toString());
        body.put("appointment_id", apptId.toString());
        body.put("kind", "REPORT");
        body.put("mime_type", mime);
        body.put("size_bytes", size);
        body.put("original_filename", filename);
        return body;
    }

    private static byte[] pdfBytes(int length) {
        byte[] b = new byte[length];
        byte[] magic = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4
        System.arraycopy(magic, 0, b, 0, Math.min(magic.length, length));
        return b;
    }

    // Tests in this class share one Postgres container, so each booking must claim a distinct
    // slot to avoid colliding with appointments left behind by earlier tests.
    private static final java.util.concurrent.atomic.AtomicInteger SLOT_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    private static String nextSlot() {
        int minutes = SLOT_SEQ.getAndIncrement() * 30;
        return nextMondayAt(9 + minutes / 60, minutes % 60);
    }

    /// Seeds an appointment for the fixture's own physiotherapist directly. Booking is now a
    /// patient request with no time, so an appointment to attach files to is seeded straight to
    /// the repository (a distinct slot keeps each one apart in the shared container).
    private UUID seedAppointment(Session physio, Session actor, UUID patientId) {
        Appointment appt = new Appointment(
                UuidV7.generate(), patientId, actor.id, physio.id,
                Instant.parse(nextSlot()), (short) 30, "file seed", null);
        appt.assignNumber(numbers.generate());
        appointments.save(appt);
        return appt.getId();
    }

    private static String nextMondayAt(int hour, int minute) {
        LocalDate today = LocalDate.now(KOLKATA);
        int daysAhead = (DayOfWeek.MONDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        if (daysAhead == 0) daysAhead = 7;
        LocalDate monday = today.plusDays(daysAhead);
        return ZonedDateTime.of(monday, LocalTime.of(hour, minute), KOLKATA).toInstant().toString();
    }

    private Session seedPhysio() {
        String email = "physio+" + UUID.randomUUID() + "@clinic.example.com";
        Account physio = new Account(
                UuidV7.generate(), email, null,
                "$argon2id$placeholder$noop", new byte[]{0},
                AccountRole.ROLE_PHYSIO);
        accounts.save(physio);
        String token = tokenIssuer.issue(physio).token();
        return new Session(physio.getId(), token);
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

        MvcResult tokensRes = mvc.perform(post("/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokenNode = json.readTree(tokensRes.getResponse().getContentAsByteArray());
        String access = tokenNode.get("access_token").asText();
        return new Session(accountIdFromAccessToken(access), access);
    }

    private UUID primaryPatientId(Session session) throws Exception {
        MvcResult res = mvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + session.access))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = json.readTree(res.getResponse().getContentAsByteArray()).get("patients");
        assertThat(list).isNotNull();
        assertThat(list.size()).isGreaterThan(0);
        return UUID.fromString(list.get(0).get("id").asText());
    }

    private UUID accountIdFromAccessToken(String access) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(access.split("\\.")[1]));
        try {
            return UUID.fromString(json.readTree(payload).get("sub").asText());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record Session(UUID id, String access) {}
}

package com.healyn.treatmentnotes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healyn.appointments.domain.Appointment;
import com.healyn.appointments.repository.AppointmentRepository;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(TreatmentNoteIntegrationTest.CapturingConfig.class)
class TreatmentNoteIntegrationTest {

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
    static class CapturingConfig {
        @Bean @Primary CapturingOtpSender capturingOtpSender() { return new CapturingOtpSender(); }
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
    @Autowired AppointmentRepository appointments;
    @Autowired AccessTokenIssuer tokenIssuer;

    @BeforeEach
    void reset() {
        otpSender.latestByTarget.clear();
    }

    @Test
    void physio_writes_then_anyone_with_access_reads() throws Exception {
        Fixture f = bootstrapCompleted("anna");

        mvc.perform(put("/appointments/" + f.apptId + "/treatment_note")
                        .header("Authorization", "Bearer " + f.physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "diagnosis", "Lumbar strain",
                                "notes", "Tender at L4-L5",
                                "recovery_instructions", "Heat + stretches"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Lumbar strain"))
                .andExpect(jsonPath("$.author_account_id").value(f.physio.id.toString()));

        mvc.perform(get("/appointments/" + f.apptId + "/treatment_note")
                        .header("Authorization", "Bearer " + f.account.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Tender at L4-L5"));
    }

    @Test
    void write_rejected_when_appointment_not_completed() throws Exception {
        Fixture f = bootstrapBooked("ben"); // CONFIRMED, not yet completed

        mvc.perform(put("/appointments/" + f.apptId + "/treatment_note")
                        .header("Authorization", "Bearer " + f.physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("diagnosis", "n/a"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("treatment_notes.appointment_not_completed"));
    }

    @Test
    void patient_side_cannot_write() throws Exception {
        Fixture f = bootstrapCompleted("cara");

        mvc.perform(put("/appointments/" + f.apptId + "/treatment_note")
                        .header("Authorization", "Bearer " + f.account.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("diagnosis", "self-diagnosis"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void empty_note_is_rejected() throws Exception {
        Fixture f = bootstrapCompleted("dan");

        mvc.perform(put("/appointments/" + f.apptId + "/treatment_note")
                        .header("Authorization", "Bearer " + f.physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("treatment_notes.empty"));
    }

    @Test
    void put_replaces_existing_note_in_place() throws Exception {
        Fixture f = bootstrapCompleted("eli");

        String firstId = upsert(f.physio, f.apptId, Map.of("diagnosis", "first"));
        String secondId = upsert(f.physio, f.apptId, Map.of("diagnosis", "second"));
        assertThat(secondId).isEqualTo(firstId);

        mvc.perform(get("/appointments/" + f.apptId + "/treatment_note")
                        .header("Authorization", "Bearer " + f.physio.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("second"));
    }

    @Test
    void patient_timeline_lists_notes() throws Exception {
        Fixture f = bootstrapCompleted("fae");
        upsert(f.physio, f.apptId, Map.of("diagnosis", "visit one"));

        mvc.perform(get("/patients/" + f.patientId + "/treatment_notes")
                        .header("Authorization", "Bearer " + f.account.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].diagnosis").value("visit one"));
    }

    // ---- fixtures + helpers ----

    private record Fixture(Session physio, Session account, UUID patientId, UUID apptId) {}

    private Fixture bootstrapBooked(String tag) throws Exception {
        Session physio = seedPhysio();
        Session account = registerPatient(tag);
        UUID patientId = primaryPatientId(account);
        UUID apptId = seedScheduledAppointment(physio, account, patientId);
        return new Fixture(physio, account, patientId, apptId);
    }

    private Fixture bootstrapCompleted(String tag) throws Exception {
        Fixture f = bootstrapBooked(tag);
        // The fixture is seeded already CONFIRMED (the physiotherapist's /schedule is exercised
        // in the appointments suite), so the lifecycle resumes at IN_PROGRESS.
        transition(f.physio, f.apptId, "IN_PROGRESS");
        transition(f.physio, f.apptId, "COMPLETED");
        return f;
    }

    private String upsert(Session actor, UUID apptId, Map<String, Object> body) throws Exception {
        MvcResult res = mvc.perform(put("/appointments/" + apptId + "/treatment_note")
                        .header("Authorization", "Bearer " + actor.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsByteArray()).get("id").asText();
    }

    private void transition(Session actor, UUID apptId, String to) throws Exception {
        mvc.perform(post("/appointments/" + apptId + "/transitions")
                        .header("Authorization", "Bearer " + actor.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("to", to))))
                .andExpect(status().isOk());
    }

    // Tests in this class share one Postgres container, so each booking must claim a distinct
    // slot to avoid colliding with appointments left behind by earlier tests.
    private static final java.util.concurrent.atomic.AtomicInteger SLOT_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    private static String nextSlot() {
        int minutes = SLOT_SEQ.getAndIncrement() * 30;
        return nextMondayAt(9 + minutes / 60, minutes % 60);
    }

    /// Seeds a CONFIRMED appointment for the fixture's own physiotherapist directly, so the
    /// treatment-note lifecycle (resuming at IN_PROGRESS -> COMPLETED) has a concrete time —
    /// a request carries none and the physiotherapist's /schedule is covered by the appointments
    /// suite. nextSlot() gives each one a distinct time so they never collide on the
    /// physio-overlap guard.
    private UUID seedScheduledAppointment(Session physio, Session actor, UUID patientId) {
        Instant at = Instant.parse(nextSlot());
        Appointment appt = new Appointment(
                UuidV7.generate(), patientId, actor.id, physio.id, at, (short) 30, "treatment-note seed", null);
        appt.schedule(at, (short) 30, Instant.now());
        appointments.save(appt);
        return appt.getId();
    }

    private static String nextMondayAt(int hour, int minute) {
        LocalDate today = LocalDate.now(KOLKATA);
        int daysAhead = (DayOfWeek.MONDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        if (daysAhead == 0) daysAhead = 7;
        LocalDate monday = today.plusDays(daysAhead);
        return ZonedDateTime.of(monday, LocalTime.of(hour, minute), KOLKATA)
                .toInstant()
                .toString();
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

package com.healyn.discussion;

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
import com.healyn.discussion.domain.DiscussionMessage;
import com.healyn.discussion.domain.DiscussionMessageType;
import com.healyn.discussion.domain.DiscussionSenderRole;
import com.healyn.discussion.repository.DiscussionMessageRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
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
@Import(DiscussionIntegrationTest.CapturingConfig.class)
class DiscussionIntegrationTest {

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
    @Autowired DiscussionMessageRepository messages;
    @Autowired AccessTokenIssuer tokenIssuer;

    @BeforeEach
    void reset() {
        otpSender.latestByTarget.clear();
    }

    @Test
    void post_then_list_returns_message_in_thread() throws Exception {
        Fixture f = bootstrap("anna");
        postMessage(f.account, f.apptId, "Hi, what should I do?", DiscussionMessageType.QUESTION);

        mvc.perform(get("/appointments/" + f.apptId + "/messages")
                        .header("Authorization", "Bearer " + f.account.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("Hi, what should I do?"))
                .andExpect(jsonPath("$.items[0].sender_role").value("PATIENT_SIDE"))
                .andExpect(jsonPath("$.items[0].message_type").value("QUESTION"));
    }

    @Test
    void physio_can_post_instruction_patient_side_cannot() throws Exception {
        Fixture f = bootstrap("ben");

        mvc.perform(post("/appointments/" + f.apptId + "/messages")
                        .header("Authorization", "Bearer " + f.account.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "message_type", "INSTRUCTION",
                                "body", "do these stretches twice daily"))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/appointments/" + f.apptId + "/messages")
                        .header("Authorization", "Bearer " + f.physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "message_type", "INSTRUCTION",
                                "body", "do these stretches twice daily"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender_role").value("PHYSIO"));
    }

    @Test
    void edit_by_non_sender_returns_403_not_sender() throws Exception {
        Fixture f = bootstrap("cara");
        String msgId = postMessage(f.account, f.apptId, "first", DiscussionMessageType.REPLY);

        mvc.perform(patch("/appointments/" + f.apptId + "/messages/" + msgId)
                        .header("Authorization", "Bearer " + f.physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", "tampered"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("discussion.not_sender"));
    }

    @Test
    void delete_by_sender_within_window_then_list_excludes_it() throws Exception {
        Fixture f = bootstrap("dan");
        String msgId = postMessage(f.account, f.apptId, "oops", DiscussionMessageType.REPLY);

        mvc.perform(delete("/appointments/" + f.apptId + "/messages/" + msgId)
                        .header("Authorization", "Bearer " + f.account.access))
                .andExpect(status().isNoContent());

        mvc.perform(get("/appointments/" + f.apptId + "/messages")
                        .header("Authorization", "Bearer " + f.account.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void patient_side_cannot_post_to_cancelled_appointment() throws Exception {
        Fixture f = bootstrap("eli");
        // Directly cancel via the repository to avoid running the cancel through the controller.
        Appointment appt = appointments.findById(f.apptId).orElseThrow();
        appt.cancel(Instant.now(), com.healyn.appointments.domain.AppointmentCancelReason.PATIENT_CANCELLED, null);
        appointments.save(appt);

        mvc.perform(post("/appointments/" + f.apptId + "/messages")
                        .header("Authorization", "Bearer " + f.account.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "message_type", "REPLY",
                                "body", "are we still on?"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("discussion.appointment_terminal"));
    }

    @Test
    void unread_count_drops_when_marker_advances() throws Exception {
        Fixture f = bootstrap("fae");
        postMessage(f.physio, f.apptId, "physio one", DiscussionMessageType.REPLY);
        String latestId = postMessage(f.physio, f.apptId, "physio two", DiscussionMessageType.REPLY);

        mvc.perform(get("/appointments/" + f.apptId + "/messages/unread-count")
                        .header("Authorization", "Bearer " + f.account.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread_count").value(2));

        mvc.perform(post("/appointments/" + f.apptId + "/messages/read")
                        .header("Authorization", "Bearer " + f.account.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("message_id", latestId))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/appointments/" + f.apptId + "/messages/unread-count")
                        .header("Authorization", "Bearer " + f.account.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread_count").value(0));
    }

    @Test
    void list_supports_cursor_pagination() throws Exception {
        Fixture f = bootstrap("gil");
        for (int i = 0; i < 12; i++) {
            DiscussionMessage m = new DiscussionMessage(
                    UuidV7.generate(),
                    f.apptId,
                    f.account.id,
                    DiscussionSenderRole.PATIENT_SIDE,
                    DiscussionMessageType.REPLY,
                    "seed " + i);
            messages.save(m);
        }

        int seen = 0;
        String cursor = null;
        for (int page = 0; page < 4 && (page == 0 || cursor != null); page++) {
            var req = get("/appointments/" + f.apptId + "/messages")
                    .header("Authorization", "Bearer " + f.account.access)
                    .param("limit", "5");
            if (cursor != null) req = req.param("cursor", cursor);
            MvcResult res = mvc.perform(req).andExpect(status().isOk()).andReturn();
            JsonNode node = json.readTree(res.getResponse().getContentAsByteArray());
            seen += node.get("items").size();
            JsonNode cursorNode = node.get("next_cursor"); // omitted entirely on the last page
            cursor = (cursorNode == null || cursorNode.isNull()) ? null : cursorNode.asText();
            if (cursor == null) break;
        }
        assertThat(seen).isEqualTo(12);
        assertThat(cursor).isNull();
    }

    // ---- fixtures + helpers ----

    private Fixture bootstrap(String tag) throws Exception {
        Session physio = seedPhysio();
        Session account = registerPatient(tag);
        UUID patientId = primaryPatientId(account);
        UUID apptId = seedAppointment(physio, account, patientId);
        return new Fixture(physio, account, patientId, apptId);
    }

    private record Fixture(Session physio, Session account, UUID patientId, UUID apptId) {}

    private String postMessage(Session actor, UUID apptId, String body, DiscussionMessageType type) throws Exception {
        MvcResult res = mvc.perform(post("/appointments/" + apptId + "/messages")
                        .header("Authorization", "Bearer " + actor.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "message_type", type.name(),
                                "body", body))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsByteArray()).get("id").asText();
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
    /// patient request with no time, so a thread-bearing appointment is seeded straight to the
    /// repository (a distinct slot keeps each one apart in the shared container).
    private UUID seedAppointment(Session physio, Session actor, UUID patientId) {
        Appointment appt = new Appointment(
                UuidV7.generate(), patientId, actor.id, physio.id,
                Instant.parse(nextSlot()), (short) 30, "discussion seed", null);
        appointments.save(appt);
        return appt.getId();
    }

    private static String nextMondayAt(int hour, int minute) {
        LocalDate today = LocalDate.now(KOLKATA);
        int daysAhead = (DayOfWeek.MONDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        if (daysAhead == 0) daysAhead = 7;
        LocalDate monday = today.plusDays(daysAhead);
        return ZonedDateTime.of(monday, java.time.LocalTime.of(hour, minute), KOLKATA)
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

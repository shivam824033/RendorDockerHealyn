package com.healyn.appointments;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(AppointmentIntegrationTest.CapturingConfig.class)
class AppointmentIntegrationTest {

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
    void two_patients_can_request_the_same_date_without_conflict() throws Exception {
        // Request-first: a request carries no time, so two patients asking for the same
        // date never conflict (APPOINTMENT_FLOW §2). No availability is consulted.
        seedPhysio();
        Session a = registerPatient("ann");
        Session b = registerPatient("ben");
        UUID patientA = primaryPatientId(a);
        UUID patientB = primaryPatientId(b);

        String date = requestDate(7);

        requestOk(a, patientA, date, "idem-a-1");
        requestOk(b, patientB, date, "idem-b-1");
    }

    @Test
    void request_for_a_date_without_availability_is_accepted() throws Exception {
        // No availability rule exists at all — a request still succeeds. The patient may
        // request any date regardless of whether that day's slots are booked.
        seedPhysio();
        Session a = registerPatient("carl");
        UUID patientA = primaryPatientId(a);

        UUID id = requestOk(a, patientA, requestDate(10), "idem-c-1");

        mvc.perform(get("/appointments/" + id)
                        .header("Authorization", "Bearer " + a.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                // scheduled_at is null on an unscheduled request and omitted (NON_NULL).
                .andExpect(jsonPath("$.scheduled_at").doesNotExist())
                .andExpect(jsonPath("$.requested_date").value(requestDate(10)))
                .andExpect(jsonPath("$.is_follow_up").value(false));
    }

    @Test
    void patient_cannot_transition_to_completed() throws Exception {
        seedPhysio();
        Session a = registerPatient("dora");
        UUID patientA = primaryPatientId(a);

        UUID apptId = requestOk(a, patientA, requestDate(7), "idem-d-1");

        mvc.perform(post("/appointments/" + apptId + "/transitions")
                        .header("Authorization", "Bearer " + a.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("to", "COMPLETED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patient_reschedule_creates_a_new_unscheduled_request() throws Exception {
        // A patient reschedule is a re-request: a new date, no time. The physiotherapist
        // re-assigns the time later via /schedule (APPOINTMENT_FLOW §6).
        Session physio = seedPhysio();
        Session a = registerPatient("eve");
        UUID patientA = primaryPatientId(a);
        UUID oldId = seedRequest(patientA, physio.id, accountIdOf(a), 7);

        MvcResult res = mvc.perform(post("/appointments/" + oldId + "/reschedule")
                        .header("Authorization", "Bearer " + a.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "requested_date", requestDate(14)))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(res.getResponse().getContentAsByteArray());
        assertThat(body.get("status").asText()).isEqualTo("REQUESTED");
        assertThat(body.get("rescheduled_from_id").asText()).isEqualTo(oldId.toString());
        assertThat(body.get("requested_date").asText()).isEqualTo(requestDate(14));
        assertThat(body.has("scheduled_at")).isFalse(); // unscheduled re-request (NON_NULL omits it)

        mvc.perform(get("/appointments/" + oldId)
                        .header("Authorization", "Bearer " + a.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESCHEDULED"));
    }

    @Test
    void physio_schedules_a_request_to_confirmed() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("nora");
        UUID patientA = primaryPatientId(a);
        UUID reqId = seedRequest(patientA, physio.id, accountIdOf(a), 7);

        mvc.perform(post("/appointments/" + reqId + "/schedule")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "scheduled_at", futureInstantAt(7, 9, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.scheduled_at").exists())
                .andExpect(jsonPath("$.confirmed_at").exists())
                .andExpect(jsonPath("$.duration_minutes").value(30));
    }

    @Test
    void patient_cannot_schedule_a_request() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("omar");
        UUID patientA = primaryPatientId(a);
        UUID reqId = seedRequest(patientA, physio.id, accountIdOf(a), 7);

        mvc.perform(post("/appointments/" + reqId + "/schedule")
                        .header("Authorization", "Bearer " + a.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "scheduled_at", futureInstantAt(7, 9, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isForbidden());
    }

    @Test
    void scheduling_an_overlapping_time_returns_409() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("pia");
        UUID patientA = primaryPatientId(a);
        UUID first = seedRequest(patientA, physio.id, accountIdOf(a), 8);
        UUID second = seedRequest(patientA, physio.id, accountIdOf(a), 8);

        String at = futureInstantAt(8, 11, 0);
        mvc.perform(post("/appointments/" + first + "/schedule")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("scheduled_at", at, "duration_minutes", 30))))
                .andExpect(status().isOk());
        // Same physiotherapist, overlapping time -> physio-overlap EXCLUDE constraint -> 409.
        mvc.perform(post("/appointments/" + second + "/schedule")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("scheduled_at", at, "duration_minutes", 30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("appointments.slot_unavailable"));
    }

    @Test
    void physio_creates_a_follow_up() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("quinn");
        UUID patientA = primaryPatientId(a);

        mvc.perform(post("/appointments/follow-ups")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "patient_id", patientA.toString(),
                                "scheduled_at", futureInstantAt(9, 14, 0),
                                "duration_minutes", 45,
                                "reason", "Review progress"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.is_follow_up").value(true))
                .andExpect(jsonPath("$.scheduled_at").exists())
                .andExpect(jsonPath("$.duration_minutes").value(45));
    }

    @Test
    void patient_cannot_create_a_follow_up() throws Exception {
        seedPhysio();
        Session a = registerPatient("rae");
        UUID patientA = primaryPatientId(a);

        mvc.perform(post("/appointments/follow-ups")
                        .header("Authorization", "Bearer " + a.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "patient_id", patientA.toString(),
                                "scheduled_at", futureInstantAt(9, 14, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isForbidden());
    }

    @Test
    void physio_reschedule_creates_a_confirmed_row() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("sam");
        UUID patientA = primaryPatientId(a);
        UUID confirmedId = seedConfirmed(patientA, physio.id, accountIdOf(a), 10, 9);

        mvc.perform(post("/appointments/" + confirmedId + "/reschedule")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "scheduled_at", futureInstantAt(11, 15, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.rescheduled_from_id").value(confirmedId.toString()));

        mvc.perform(get("/appointments/" + confirmedId)
                        .header("Authorization", "Bearer " + physio.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESCHEDULED"));
    }

    @Test
    void cancel_without_reason_returns_422() throws Exception {
        seedPhysio();
        Session a = registerPatient("fay");
        UUID patientA = primaryPatientId(a);

        UUID apptId = requestOk(a, patientA, requestDate(7), "idem-f-1");

        mvc.perform(post("/appointments/" + apptId + "/transitions")
                        .header("Authorization", "Bearer " + a.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("to", "CANCELLED"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("appointments.cancel_reason_required"));
    }

    @Test
    void idempotency_replay_returns_same_appointment_id() throws Exception {
        seedPhysio();
        Session a = registerPatient("gus");
        UUID patientA = primaryPatientId(a);

        String body = json.writeValueAsString(Map.of(
                "patient_id", patientA.toString(),
                "requested_date", requestDate(7)));

        MvcResult first = mvc.perform(post("/appointments")
                        .header("Authorization", "Bearer " + a.access)
                        .header("Idempotency-Key", "idem-g-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult replay = mvc.perform(post("/appointments")
                        .header("Authorization", "Bearer " + a.access)
                        .header("Idempotency-Key", "idem-g-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = json.readTree(first.getResponse().getContentAsByteArray()).get("id").asText();
        String replayId = json.readTree(replay.getResponse().getContentAsByteArray()).get("id").asText();
        assertThat(replayId).isEqualTo(firstId);
    }

    @Test
    void cursor_pagination_walks_all_pages() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("hal");
        UUID patientA = primaryPatientId(a);

        // Seed 25 appointments directly through the repository (bypassing slot validation).
        for (int i = 0; i < 25; i++) {
            Appointment appt = new Appointment(
                    UuidV7.generate(),
                    patientA,
                    a.id != null ? a.id : accountIdOf(a),
                    physio.id,
                    ZonedDateTime.now(KOLKATA).plusDays(1 + i).toInstant(),
                    (short) 30,
                    "seed-" + i,
                    null);
            appointments.save(appt);
        }

        int seen = 0;
        String cursor = null;
        for (int page = 0; page < 5 && (page == 0 || cursor != null); page++) {
            var req = get("/appointments")
                    .header("Authorization", "Bearer " + a.access)
                    .param("limit", "10");
            if (cursor != null) req = req.param("cursor", cursor);
            MvcResult res = mvc.perform(req).andExpect(status().isOk()).andReturn();
            JsonNode node = json.readTree(res.getResponse().getContentAsByteArray());
            seen += node.get("items").size();
            JsonNode cursorNode = node.get("next_cursor"); // omitted entirely on the last page
            cursor = (cursorNode == null || cursorNode.isNull()) ? null : cursorNode.asText();
            if (cursor == null) break;
        }
        assertThat(seen).isEqualTo(25);
        assertThat(cursor).isNull();
    }

    @Test
    void physio_list_filters_to_a_single_patient_via_patient_id_param() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("ida");
        Session b = registerPatient("ivy");
        UUID patientA = primaryPatientId(a);
        UUID patientB = primaryPatientId(b);

        seedAppointment(patientA, physio.id, accountIdOf(a), 1);
        seedAppointment(patientB, physio.id, accountIdOf(b), 2);

        MvcResult res = mvc.perform(get("/appointments")
                        .header("Authorization", "Bearer " + physio.access)
                        .param("patient_id", patientA.toString())
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = json.readTree(res.getResponse().getContentAsByteArray()).get("items");
        assertThat(items.size()).isGreaterThan(0);
        for (JsonNode item : items) {
            assertThat(item.get("patient_id").asText()).isEqualTo(patientA.toString());
        }
    }

    @Test
    void upcoming_lists_confirmed_ascending_and_excludes_unscheduled_requests() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("tess");
        UUID patientA = primaryPatientId(a);
        // Two CONFIRMED at distinct future times + one unscheduled REQUESTED (no time).
        seedConfirmed(patientA, physio.id, accountIdOf(a), 7, 11);
        seedConfirmed(patientA, physio.id, accountIdOf(a), 5, 9);
        seedRequest(patientA, physio.id, accountIdOf(a), 6);

        MvcResult res = mvc.perform(get("/appointments/upcoming")
                        .header("Authorization", "Bearer " + a.access))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = json.readTree(res.getResponse().getContentAsByteArray()).get("items");
        // Only the two scheduled rows; the unscheduled request is not "upcoming".
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(items.get(1).get("status").asText()).isEqualTo("CONFIRMED");
        // Ascending by scheduled time: day 5 before day 7.
        assertThat(items.get(0).get("scheduled_at").asText())
                .isLessThan(items.get(1).get("scheduled_at").asText());
        assertThat(items.get(0).get("requested_date").asText()).isEqualTo(requestDate(5));
    }

    @Test
    void upcoming_is_scoped_to_the_callers_own_patients() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("uma");
        Session b = registerPatient("vic");
        UUID patientA = primaryPatientId(a);
        UUID patientB = primaryPatientId(b);
        seedConfirmed(patientB, physio.id, accountIdOf(b), 5, 9);

        MvcResult res = mvc.perform(get("/appointments/upcoming")
                        .header("Authorization", "Bearer " + a.access))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = json.readTree(res.getResponse().getContentAsByteArray()).get("items");
        for (JsonNode item : items) {
            assertThat(item.get("patient_id").asText()).isEqualTo(patientA.toString());
        }
    }

    @Test
    void calendar_returns_only_appointments_inside_the_window() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("walt");
        UUID patientA = primaryPatientId(a);
        seedConfirmed(patientA, physio.id, accountIdOf(a), 5, 9);   // inside [day0, day30)
        seedConfirmed(patientA, physio.id, accountIdOf(a), 40, 9);  // after the window

        MvcResult res = mvc.perform(get("/appointments/calendar")
                        .header("Authorization", "Bearer " + a.access)
                        .param("from", windowEdge(0))
                        .param("to", windowEdge(30)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = json.readTree(res.getResponse().getContentAsByteArray()).get("items");
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).get("requested_date").asText()).isEqualTo(requestDate(5));
    }

    @Test
    void calendar_rejects_an_inverted_range() throws Exception {
        seedPhysio();
        Session a = registerPatient("xan");

        mvc.perform(get("/appointments/calendar")
                        .header("Authorization", "Bearer " + a.access)
                        .param("from", windowEdge(30))
                        .param("to", windowEdge(5)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("appointments.invalid_schedule"));
    }

    // ---- helpers ----

    private void seedAppointment(UUID patientId, UUID physioId, UUID bookedBy, int dayOffset) {
        appointments.save(new Appointment(
                UuidV7.generate(),
                patientId,
                bookedBy,
                physioId,
                ZonedDateTime.now(KOLKATA).plusDays(dayOffset).toInstant(),
                (short) 30,
                "seed",
                null));
    }

    private UUID requestOk(Session actor, UUID patientId, String requestedDate, String key) throws Exception {
        MvcResult res = mvc.perform(post("/appointments")
                        .header("Authorization", "Bearer " + actor.access)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "patient_id", patientId.toString(),
                                "requested_date", requestedDate))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(res.getResponse().getContentAsByteArray()).get("id").asText());
    }

    /// A valid future request date (clinic zone), within the 90-day horizon.
    private static String requestDate(int daysAhead) {
        return LocalDate.now(KOLKATA).plusDays(daysAhead).toString();
    }

    /// Seeds an unscheduled REQUESTED request (no time) directly, pinned to a known
    /// physiotherapist so the physio acting in the test is the appointment's own physio.
    private UUID seedRequest(UUID patientId, UUID physioId, UUID bookedBy, int daysAhead) {
        Appointment a = Appointment.request(
                UuidV7.generate(), patientId, bookedBy, physioId,
                LocalDate.now(KOLKATA).plusDays(daysAhead), null, "seed-request", null);
        appointments.save(a);
        return a.getId();
    }

    /// Seeds a CONFIRMED appointment with a concrete time directly.
    private UUID seedConfirmed(UUID patientId, UUID physioId, UUID bookedBy, int daysAhead, int hour) {
        Instant at = ZonedDateTime.of(
                LocalDate.now(KOLKATA).plusDays(daysAhead), LocalTime.of(hour, 0), KOLKATA).toInstant();
        Appointment a = new Appointment(
                UuidV7.generate(), patientId, bookedBy, physioId, at, (short) 30, "seed-confirmed", null);
        a.schedule(at, (short) 30, Instant.now());
        appointments.save(a);
        return a.getId();
    }

    private static String futureInstantAt(int daysAhead, int hour, int minute) {
        return ZonedDateTime.of(
                        LocalDate.now(KOLKATA).plusDays(daysAhead), LocalTime.of(hour, minute), KOLKATA)
                .toInstant()
                .toString();
    }

    /// A calendar-window edge: midnight (clinic zone) `daysAhead` days out, as an instant.
    private static String windowEdge(int daysAhead) {
        return ZonedDateTime.of(
                        LocalDate.now(KOLKATA).plusDays(daysAhead), LocalTime.MIDNIGHT, KOLKATA)
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
        String access = json.readTree(tokensRes.getResponse().getContentAsByteArray())
                .get("access_token").asText();
        return new Session(null, access);
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

    private UUID accountIdOf(Session session) {
        // Recover from the JWT — sub is the account UUID. Cheaper than decoding: use the access token's payload.
        String payload = new String(java.util.Base64.getUrlDecoder().decode(
                session.access.split("\\.")[1]));
        JsonNode node;
        try {
            node = json.readTree(payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return UUID.fromString(node.get("sub").asText());
    }

    private record Session(UUID id, String access) {}
}

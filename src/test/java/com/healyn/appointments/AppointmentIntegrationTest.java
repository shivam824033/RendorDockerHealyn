package com.healyn.appointments;

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
    @Autowired AppointmentNumberGenerator numbers;

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
    void request_carries_a_human_friendly_appointment_number() throws Exception {
        seedPhysio();
        Session a = registerPatient("zoe");
        UUID patientA = primaryPatientId(a);
        UUID id = requestOk(a, patientA, requestDate(7), "idem-z-1");

        mvc.perform(get("/appointments/" + id)
                        .header("Authorization", "Bearer " + a.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment_number")
                        .value(org.hamcrest.Matchers.matchesPattern("PHY-\\d{8}-\\d{4}")));
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
    void physio_reschedule_numbers_children_and_links_the_lineage() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("seb");
        UUID patientA = primaryPatientId(a);
        UUID rootId = seedConfirmed(patientA, physio.id, accountIdOf(a), 10, 9);
        String rootNumber = appointmentNumber(physio, rootId);

        // First reschedule: a -R1 child of the root, linked by source and root.
        MvcResult r1 = mvc.perform(post("/appointments/" + rootId + "/reschedule")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "scheduled_at", futureInstantAt(11, 15, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointment_number").value(rootNumber + "-R1"))
                .andExpect(jsonPath("$.child_kind").value("RESCHEDULE"))
                .andExpect(jsonPath("$.root_appointment_id").value(rootId.toString()))
                .andExpect(jsonPath("$.source_appointment_id").value(rootId.toString()))
                .andReturn();
        UUID r1Id = UUID.fromString(
                json.readTree(r1.getResponse().getContentAsByteArray()).get("id").asText());

        // Rescheduling the -R1 row again continues the same lineage as -R2 (root stem preserved).
        mvc.perform(post("/appointments/" + r1Id + "/reschedule")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "scheduled_at", futureInstantAt(12, 15, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointment_number").value(rootNumber + "-R2"))
                .andExpect(jsonPath("$.child_kind").value("RESCHEDULE"))
                .andExpect(jsonPath("$.root_appointment_id").value(rootId.toString()))
                .andExpect(jsonPath("$.source_appointment_id").value(r1Id.toString()));
    }

    @Test
    void follow_up_with_a_source_is_numbered_and_linked_as_a_child() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("tom");
        UUID patientA = primaryPatientId(a);
        UUID rootId = seedConfirmed(patientA, physio.id, accountIdOf(a), 10, 9);
        String rootNumber = appointmentNumber(physio, rootId);

        mvc.perform(post("/appointments/follow-ups")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "patient_id", patientA.toString(),
                                "source_appointment_id", rootId.toString(),
                                "scheduled_at", futureInstantAt(20, 14, 0),
                                "duration_minutes", 45,
                                "reason", "Review progress"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.is_follow_up").value(true))
                .andExpect(jsonPath("$.appointment_number").value(rootNumber + "-F1"))
                .andExpect(jsonPath("$.child_kind").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.root_appointment_id").value(rootId.toString()))
                .andExpect(jsonPath("$.source_appointment_id").value(rootId.toString()));
    }

    @Test
    void standalone_follow_up_is_its_own_root_with_a_per_day_number() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("uri");
        UUID patientA = primaryPatientId(a);

        MvcResult res = mvc.perform(post("/appointments/follow-ups")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "patient_id", patientA.toString(),
                                "scheduled_at", futureInstantAt(21, 14, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.is_follow_up").value(true))
                // No source -> a normal per-day number (no child suffix) and child_kind omitted.
                .andExpect(jsonPath("$.appointment_number")
                        .value(org.hamcrest.Matchers.matchesPattern("PHY-\\d{8}-\\d{4}")))
                .andExpect(jsonPath("$.child_kind").doesNotExist())
                .andReturn();
        JsonNode body = json.readTree(res.getResponse().getContentAsByteArray());
        // A root is its own lineage root and has no source.
        assertThat(body.get("root_appointment_id").asText()).isEqualTo(body.get("id").asText());
        assertThat(body.has("source_appointment_id")).isFalse();
    }

    @Test
    void timeline_tells_the_full_lineage_story_from_any_member() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("yan");
        UUID patientA = primaryPatientId(a);

        // Book through the API so every lifecycle step lands on the timeline.
        UUID rootId = requestOk(a, patientA, requestDate(7), "idem-y-1");
        mvc.perform(post("/appointments/" + rootId + "/schedule")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "scheduled_at", futureInstantAt(7, 9, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isOk());
        MvcResult res = mvc.perform(post("/appointments/" + rootId + "/reschedule")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "scheduled_at", futureInstantAt(8, 10, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID childId = UUID.fromString(
                json.readTree(res.getResponse().getContentAsByteArray()).get("id").asText());
        String rootNumber = appointmentNumber(physio, rootId);

        // Reading the timeline from the CHILD shows the whole lineage, oldest first.
        mvc.perform(get("/appointments/" + childId + "/timeline")
                        .header("Authorization", "Bearer " + a.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].event_type").value("CREATED"))
                .andExpect(jsonPath("$.items[0].appointment_id").value(rootId.toString()))
                .andExpect(jsonPath("$.items[0].appointment_number").value(rootNumber))
                .andExpect(jsonPath("$.items[0].actor_role").value("ROLE_ACCOUNT"))
                .andExpect(jsonPath("$.items[1].event_type").value("SCHEDULED"))
                .andExpect(jsonPath("$.items[1].actor_role").value("ROLE_PHYSIO"))
                .andExpect(jsonPath("$.items[2].event_type").value("RESCHEDULED"))
                .andExpect(jsonPath("$.items[2].appointment_id").value(rootId.toString()))
                .andExpect(jsonPath("$.items[2].related_appointment_id").value(childId.toString()))
                .andExpect(jsonPath("$.items[3].event_type").value("CREATED"))
                .andExpect(jsonPath("$.items[3].appointment_id").value(childId.toString()))
                .andExpect(jsonPath("$.items[3].appointment_number").value(rootNumber + "-R1"))
                .andExpect(jsonPath("$.items[3].child_kind").value("RESCHEDULE"))
                .andExpect(jsonPath("$.items[3].related_appointment_id").value(rootId.toString()));
    }

    @Test
    void timeline_records_in_place_transitions_with_their_actors() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("zed");
        UUID patientA = primaryPatientId(a);

        UUID id = requestOk(a, patientA, requestDate(7), "idem-zd-1");
        mvc.perform(post("/appointments/" + id + "/schedule")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "scheduled_at", futureInstantAt(7, 11, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isOk());
        mvc.perform(post("/appointments/" + id + "/transitions")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("to", "IN_PROGRESS"))))
                .andExpect(status().isOk());
        mvc.perform(post("/appointments/" + id + "/transitions")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("to", "COMPLETED"))))
                .andExpect(status().isOk());

        mvc.perform(get("/appointments/" + id + "/timeline")
                        .header("Authorization", "Bearer " + a.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[2].event_type").value("STARTED"))
                .andExpect(jsonPath("$.items[3].event_type").value("COMPLETED"))
                .andExpect(jsonPath("$.items[3].actor_role").value("ROLE_PHYSIO"));
    }

    @Test
    void timeline_cancellation_carries_the_reason_enum_only() throws Exception {
        seedPhysio();
        Session a = registerPatient("ada");
        UUID patientA = primaryPatientId(a);

        UUID id = requestOk(a, patientA, requestDate(7), "idem-ad-1");
        mvc.perform(post("/appointments/" + id + "/transitions")
                        .header("Authorization", "Bearer " + a.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "to", "CANCELLED",
                                "cancel_reason", "PATIENT_CANCELLED",
                                "cancel_note", "family emergency"))))
                .andExpect(status().isOk());

        MvcResult res = mvc.perform(get("/appointments/" + id + "/timeline")
                        .header("Authorization", "Bearer " + a.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].event_type").value("CANCELLED"))
                .andExpect(jsonPath("$.items[1].cancel_reason").value("PATIENT_CANCELLED"))
                .andReturn();
        // PHI-free: the free-text cancel note must never appear on the timeline.
        assertThat(res.getResponse().getContentAsString()).doesNotContain("family emergency");
    }

    @Test
    void timeline_is_not_visible_to_an_unrelated_account() throws Exception {
        seedPhysio();
        Session a = registerPatient("bea");
        Session other = registerPatient("cal");
        UUID patientA = primaryPatientId(a);
        UUID id = requestOk(a, patientA, requestDate(7), "idem-b-2");

        mvc.perform(get("/appointments/" + id + "/timeline")
                        .header("Authorization", "Bearer " + other.access))
                .andExpect(status().isForbidden());

        mvc.perform(get("/appointments/" + UUID.randomUUID() + "/timeline")
                        .header("Authorization", "Bearer " + a.access))
                .andExpect(status().isNotFound());
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
    void physio_rejects_a_request_to_a_terminal_rejected_state() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("rio");
        UUID patientA = primaryPatientId(a);
        UUID reqId = seedRequest(patientA, physio.id, accountIdOf(a), 7);

        mvc.perform(post("/appointments/" + reqId + "/transitions")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "to", "REJECTED",
                                "cancel_note", "Not accepting new patients this month"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                // A rejection is not a cancellation: no cancel_reason is recorded.
                .andExpect(jsonPath("$.cancel_reason").doesNotExist());

        // Terminal: a rejected request cannot be moved on.
        mvc.perform(post("/appointments/" + reqId + "/transitions")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("to", "IN_PROGRESS"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("appointments.invalid_transition"));
    }

    @Test
    void patient_cannot_reject_a_request() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("sky");
        UUID patientA = primaryPatientId(a);
        UUID reqId = seedRequest(patientA, physio.id, accountIdOf(a), 7);

        mvc.perform(post("/appointments/" + reqId + "/transitions")
                        .header("Authorization", "Bearer " + a.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("to", "REJECTED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void timeline_records_a_rejection_without_leaking_the_note() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("teo");
        UUID patientA = primaryPatientId(a);
        // Book through the API so the request lands a CREATED event before the rejection.
        UUID reqId = requestOk(a, patientA, requestDate(7), "idem-teo-1");

        mvc.perform(post("/appointments/" + reqId + "/transitions")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "to", "REJECTED",
                                "cancel_note", "schedule is full"))))
                .andExpect(status().isOk());

        MvcResult res = mvc.perform(get("/appointments/" + reqId + "/timeline")
                        .header("Authorization", "Bearer " + a.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].event_type").value("CREATED"))
                .andExpect(jsonPath("$.items[1].event_type").value("REJECTED"))
                .andExpect(jsonPath("$.items[1].actor_role").value("ROLE_PHYSIO"))
                // A rejection carries no cancel_reason on the timeline.
                .andExpect(jsonPath("$.items[1].cancel_reason").doesNotExist())
                .andReturn();
        // PHI-free: the free-text rejection note must never appear on the timeline.
        assertThat(res.getResponse().getContentAsString()).doesNotContain("schedule is full");
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
            appt.assignNumber(numbers.generate());
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
    void list_filters_by_is_follow_up() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("flo");
        UUID patientA = primaryPatientId(a);
        // One ordinary appointment + one physio-created follow-up for the same patient.
        seedConfirmed(patientA, physio.id, accountIdOf(a), 5, 9);
        mvc.perform(post("/appointments/follow-ups")
                        .header("Authorization", "Bearer " + physio.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "patient_id", patientA.toString(),
                                "scheduled_at", futureInstantAt(9, 14, 0),
                                "duration_minutes", 30))))
                .andExpect(status().isCreated());

        // is_follow_up=true returns only the follow-up.
        JsonNode followUps = listItems(physio, patientA, "true");
        assertThat(followUps.size()).isEqualTo(1);
        assertThat(followUps.get(0).get("is_follow_up").asBoolean()).isTrue();

        // is_follow_up=false returns only the ordinary appointment.
        JsonNode ordinary = listItems(physio, patientA, "false");
        assertThat(ordinary.size()).isEqualTo(1);
        assertThat(ordinary.get(0).get("is_follow_up").asBoolean()).isFalse();

        // No filter returns both.
        JsonNode all = listItems(physio, patientA, null);
        assertThat(all.size()).isEqualTo(2);
    }

    private JsonNode listItems(Session actor, UUID patientId, String isFollowUp) throws Exception {
        var req = get("/appointments")
                .header("Authorization", "Bearer " + actor.access)
                .param("patient_id", patientId.toString())
                .param("limit", "50");
        if (isFollowUp != null) req = req.param("is_follow_up", isFollowUp);
        MvcResult res = mvc.perform(req).andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsByteArray()).get("items");
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

    @Test
    void search_finds_an_appointment_by_patient_name() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("ned");
        UUID patientA = primaryPatientId(a);
        UUID id = requestOk(a, patientA, requestDate(7), "idem-ned-1");

        JsonNode items = searchItems(physio, "ned");
        boolean found = false;
        for (JsonNode it : items) {
            if (it.get("appointment_id").asText().equals(id.toString())) {
                found = true;
                assertThat(it.get("patient_name").asText()).contains("ned");
                assertThat(it.get("patient_id").asText()).isEqualTo(patientA.toString());
                assertThat(it.get("appointment_number").asText()).startsWith("PHY-");
            }
        }
        assertThat(found).as("search by patient name finds the appointment").isTrue();
    }

    @Test
    void search_matches_the_appointment_number_prefix_case_insensitively() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("mae");
        UUID patientA = primaryPatientId(a);
        UUID id = requestOk(a, patientA, requestDate(7), "idem-mae-1");
        String number = appointmentNumber(a, id); // PHY-YYYYMMDD-NNNN

        // Full number, a date-stem prefix, and a lower-cased term all match (the service
        // upper-cases the term to hit the upper-case-stored numbers via the prefix index).
        assertThat(containsAppointment(searchItems(physio, number), id)).isTrue();
        assertThat(containsAppointment(searchItems(physio, number.substring(0, 12)), id)).isTrue();
        assertThat(containsAppointment(searchItems(physio, number.toLowerCase()), id)).isTrue();
    }

    @Test
    void search_matches_the_patient_number() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("lou");
        UUID patientA = primaryPatientId(a);
        UUID id = requestOk(a, patientA, requestDate(7), "idem-lou-1");
        String patientNumber = patientNumberOf(a); // PAT-NNNNNN

        JsonNode items = searchItems(physio, patientNumber);
        assertThat(containsAppointment(items, id)).isTrue();
        for (JsonNode it : items) {
            if (it.get("appointment_id").asText().equals(id.toString())) {
                assertThat(it.get("patient_number").asText()).isEqualTo(patientNumber);
            }
        }
    }

    @Test
    void search_is_scoped_to_the_callers_own_patients() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("kim");
        Session b = registerPatient("kev");
        UUID patientA = primaryPatientId(a);
        UUID patientB = primaryPatientId(b);
        UUID idA = requestOk(a, patientA, requestDate(7), "idem-kim-1");
        UUID idB = requestOk(b, patientB, requestDate(7), "idem-kev-1");

        // An account never finds another account's appointment, even by a matching name.
        assertThat(containsAppointment(searchItems(a, "kev"), idB)).isFalse();
        assertThat(containsAppointment(searchItems(a, "kim"), idA)).isTrue();

        // The physiotherapist sees across patients.
        assertThat(containsAppointment(searchItems(physio, "kim"), idA)).isTrue();
        assertThat(containsAppointment(searchItems(physio, "kev"), idB)).isTrue();
    }

    @Test
    void search_returns_nothing_for_a_term_shorter_than_two_characters() throws Exception {
        Session physio = seedPhysio();
        Session a = registerPatient("jo");
        UUID patientA = primaryPatientId(a);
        requestOk(a, patientA, requestDate(7), "idem-jo-1");

        assertThat(searchItems(physio, "j").size()).isZero();
    }

    // ---- helpers ----

    private JsonNode searchItems(Session actor, String q) throws Exception {
        MvcResult res = mvc.perform(get("/appointments/search")
                        .header("Authorization", "Bearer " + actor.access)
                        .param("q", q)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsByteArray()).get("items");
    }

    private static boolean containsAppointment(JsonNode items, UUID id) {
        for (JsonNode it : items) {
            if (it.get("appointment_id").asText().equals(id.toString())) return true;
        }
        return false;
    }

    /// Reads the caller's primary patient's human-friendly number (PAT-…) via the API.
    private String patientNumberOf(Session session) throws Exception {
        MvcResult res = mvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + session.access))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = json.readTree(res.getResponse().getContentAsByteArray()).get("patients");
        return list.get(0).get("patient_number").asText();
    }

    private void seedAppointment(UUID patientId, UUID physioId, UUID bookedBy, int dayOffset) {
        Appointment a = new Appointment(
                UuidV7.generate(),
                patientId,
                bookedBy,
                physioId,
                ZonedDateTime.now(KOLKATA).plusDays(dayOffset).toInstant(),
                (short) 30,
                "seed",
                null);
        a.assignNumber(numbers.generate());
        appointments.save(a);
    }

    /// Reads an appointment's human-friendly number via the API (the root stem children hang off).
    private String appointmentNumber(Session actor, UUID appointmentId) throws Exception {
        MvcResult res = mvc.perform(get("/appointments/" + appointmentId)
                        .header("Authorization", "Bearer " + actor.access))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsByteArray()).get("appointment_number").asText();
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
        a.assignNumber(numbers.generate());
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
        a.assignNumber(numbers.generate());
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

package com.healyn.patients;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(PatientIntegrationTest.CapturingConfig.class)
class PatientIntegrationTest {

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
    void list_after_registration_returns_only_primary_patient() throws Exception {
        Session s = register("alice");
        mvc.perform(get("/patients").header("Authorization", "Bearer " + s.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(1))
                .andExpect(jsonPath("$.patients[0].primary").value(true))
                .andExpect(jsonPath("$.patients[0].relationship").value("SELF"));
    }

    @Test
    void add_family_member_appears_in_list() throws Exception {
        Session s = register("bob");
        String body = json.writeValueAsString(Map.of(
                "full_name", "Bob Jr",
                "date_of_birth", "2015-04-10",
                "sex", "MALE",
                "relationship", "CHILD",
                "authority_attested", true));

        mvc.perform(post("/patients")
                        .header("Authorization", "Bearer " + s.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relationship").value("CHILD"))
                .andExpect(jsonPath("$.primary").value(false));

        mvc.perform(get("/patients").header("Authorization", "Bearer " + s.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(2));
    }

    @Test
    void patients_have_distinct_human_friendly_numbers() throws Exception {
        Session s = register("heidi");
        createFamilyMember(s, "Heidi Jr", "2019-03-03", "FEMALE", "CHILD");

        MvcResult res = mvc.perform(get("/patients").header("Authorization", "Bearer " + s.access))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode patients = json.readTree(res.getResponse().getContentAsByteArray()).get("patients");
        assertThat(patients).hasSize(2);
        java.util.Set<String> numbers = new java.util.HashSet<>();
        for (JsonNode p : patients) {
            String number = p.get("patient_number").asText();
            assertThat(number).as("human-friendly id format").matches("PAT-\\d+");
            numbers.add(number);
        }
        assertThat(numbers).as("each patient has a distinct PAT- number").hasSize(2);
    }

    @Test
    void patch_updates_fields_on_managed_patient() throws Exception {
        Session s = register("carol");
        UUID child = createFamilyMember(s, "Carol Jr", "2018-06-01", "FEMALE", "CHILD");

        mvc.perform(patch("/patients/" + child)
                        .header("Authorization", "Bearer " + s.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "full_name", "Carol Junior",
                                "allergies", "Peanuts"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Carol Junior"))
                .andExpect(jsonPath("$.allergies").value("Peanuts"));
    }

    @Test
    void delete_primary_patient_returns_422() throws Exception {
        Session s = register("dave");
        Map<String, Object> list = body(mvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + s.access))
                .andExpect(status().isOk())
                .andReturn());
        @SuppressWarnings("unchecked")
        Map<String, Object> primary = ((java.util.List<Map<String, Object>>) list.get("patients")).get(0);

        mvc.perform(delete("/patients/" + primary.get("id"))
                        .header("Authorization", "Bearer " + s.access))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("patients.primary_required"));
    }

    @Test
    void delete_family_member_removes_link_and_hides_from_list() throws Exception {
        Session s = register("erin");
        UUID child = createFamilyMember(s, "Erin Jr", "2020-08-08", "OTHER", "CHILD");

        mvc.perform(delete("/patients/" + child)
                        .header("Authorization", "Bearer " + s.access))
                .andExpect(status().isNoContent());

        mvc.perform(get("/patients").header("Authorization", "Bearer " + s.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(1));

        mvc.perform(get("/patients/" + child)
                        .header("Authorization", "Bearer " + s.access))
                .andExpect(status().isForbidden());
    }

    @Test
    void other_account_cannot_access_my_patient() throws Exception {
        Session owner = register("frank");
        Session intruder = register("grace");

        UUID child = createFamilyMember(owner, "Frank Jr", "2017-02-02", "MALE", "CHILD");

        mvc.perform(get("/patients/" + child)
                        .header("Authorization", "Bearer " + intruder.access))
                .andExpect(status().isForbidden());
    }

    @Test
    void registration_captures_household_address_on_primary_patient() throws Exception {
        Session s = register("ivan");
        mvc.perform(get("/patients").header("Authorization", "Bearer " + s.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients[0].address.line1").value("1 Test Street"))
                .andExpect(jsonPath("$.patients[0].address.city").value("Pune"))
                .andExpect(jsonPath("$.patients[0].address.state").value("Maharashtra"))
                .andExpect(jsonPath("$.patients[0].address.postal_code").value("411001"))
                .andExpect(jsonPath("$.patients[0].address.country").value("India"));
    }

    @Test
    void family_member_shares_the_account_household_address() throws Exception {
        Session s = register("judy");
        createFamilyMember(s, "Judy Jr", "2016-07-07", "FEMALE", "CHILD");

        MvcResult res = mvc.perform(get("/patients").header("Authorization", "Bearer " + s.access))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode patients = json.readTree(res.getResponse().getContentAsByteArray()).get("patients");
        assertThat(patients).hasSize(2);
        for (JsonNode p : patients) {
            assertThat(p.get("address").get("line1").asText()).isEqualTo("1 Test Street");
            assertThat(p.get("address").get("postal_code").asText()).isEqualTo("411001");
        }
    }

    @Test
    void get_account_address_returns_the_household_address() throws Exception {
        Session s = register("kevin");
        mvc.perform(get("/account/address").header("Authorization", "Bearer " + s.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.line1").value("1 Test Street"))
                .andExpect(jsonPath("$.address.city").value("Pune"));
    }

    @Test
    void put_account_address_updates_the_household_for_every_patient() throws Exception {
        Session s = register("laura");
        UUID child = createFamilyMember(s, "Laura Jr", "2014-02-02", "OTHER", "CHILD");

        mvc.perform(put("/account/address")
                        .header("Authorization", "Bearer " + s.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "line1", "42 New Road",
                                "line2", "Flat 3",
                                "city", "Mumbai",
                                "state", "Maharashtra",
                                "postal_code", "400001",
                                "country", "India"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line1").value("42 New Road"))
                .andExpect(jsonPath("$.line2").value("Flat 3"));

        // The move is reflected on the family member's view too (one shared household).
        mvc.perform(get("/patients/" + child).header("Authorization", "Bearer " + s.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.line1").value("42 New Road"))
                .andExpect(jsonPath("$.address.city").value("Mumbai"));
    }

    @Test
    void put_account_address_rejects_missing_required_fields() throws Exception {
        Session s = register("mallory");
        mvc.perform(put("/account/address")
                        .header("Authorization", "Bearer " + s.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("line1", "Only line one"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("common.validation_failed"));
    }

    @Test
    void physio_roster_search_by_name_finds_the_patient() throws Exception {
        // Lowercase: account emails normalise to lowercase, so register()'s OTP lookup
        // (keyed by the email) only resolves for a lowercase prefix. Search is ILIKE, so
        // case doesn't matter for the assertion.
        String unique = "zenph" + UUID.randomUUID().toString().substring(0, 8);
        register(unique);
        String physio = seedPhysio();

        mvc.perform(get("/patients").param("q", unique)
                        .header("Authorization", "Bearer " + physio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(1))
                .andExpect(jsonPath("$.patients[0].full_name").value(unique + " Person"));
    }

    @Test
    void physio_roster_search_by_patient_id_returns_exact_patient() throws Exception {
        Session s = register("idsearch");
        // The patient learns its own PAT- number from its list; the physio searches by it.
        JsonNode mine = json.readTree(mvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + s.access))
                .andReturn().getResponse().getContentAsByteArray()).get("patients").get(0);
        String number = mine.get("patient_number").asText();
        String physio = seedPhysio();

        mvc.perform(get("/patients").param("q", number)
                        .header("Authorization", "Bearer " + physio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(1))
                .andExpect(jsonPath("$.patients[0].patient_number").value(number));
    }

    @Test
    void physio_roster_paginates_and_returns_next_cursor() throws Exception {
        register("pageone");
        register("pagetwo");
        String physio = seedPhysio();

        JsonNode first = json.readTree(mvc.perform(get("/patients").param("limit", "1")
                        .header("Authorization", "Bearer " + physio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(1))
                .andExpect(jsonPath("$.next_cursor").isNotEmpty())
                .andReturn().getResponse().getContentAsByteArray());
        String firstId = first.get("patients").get(0).get("id").asText();
        String cursor = first.get("next_cursor").asText();

        // The second page starts after the cursor — a different patient (newest-first keyset).
        mvc.perform(get("/patients").param("limit", "1").param("cursor", cursor)
                        .header("Authorization", "Bearer " + physio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(1))
                .andExpect(jsonPath("$.patients[0].id").value(org.hamcrest.Matchers.not(firstId)));
    }

    @Test
    void physio_roster_rejects_a_malformed_cursor() throws Exception {
        String physio = seedPhysio();
        mvc.perform(get("/patients").param("cursor", "not-a-valid-cursor!!")
                        .header("Authorization", "Bearer " + physio))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("common.invalid_cursor"));
    }

    /// Seeds a physiotherapist account directly (there is no physio self-registration) and
    /// returns its access token. Mirrors the helper in AppointmentIntegrationTest.
    private String seedPhysio() {
        Account physio = new Account(
                UuidV7.generate(), "physio+" + UUID.randomUUID() + "@clinic.example.com", null,
                "$argon2id$placeholder$noop", new byte[]{0}, AccountRole.ROLE_PHYSIO);
        accounts.save(physio);
        return tokenIssuer.issue(physio, UUID.randomUUID()).token();
    }

    private UUID createFamilyMember(Session s, String name, String dob, String sex, String rel) throws Exception {
        Map<String, Object> resp = body(mvc.perform(post("/patients")
                        .header("Authorization", "Bearer " + s.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "full_name", name,
                                "date_of_birth", dob,
                                "sex", sex,
                                "relationship", rel,
                                "authority_attested", true))))
                .andExpect(status().isCreated())
                .andReturn());
        return UUID.fromString((String) resp.get("id"));
    }

    private Session register(String prefix) throws Exception {
        String email = prefix + "+" + UUID.randomUUID() + "@example.com";
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
                "full_name", prefix + " Person",
                "date_of_birth", "1991-05-20",
                "sex", "UNDISCLOSED"));
        body.put("consents", Map.of("terms_accepted", true, "privacy_accepted", true, "health_data_processing_accepted", true));
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
        return new Session((String) tokens.get("access_token"));
    }

    private Map<String, Object> body(MvcResult result) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsByteArray());
        Map<String, Object> map = new HashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(),
                e.getValue().isTextual() ? e.getValue().asText() : json.convertValue(e.getValue(), Object.class)));
        return map;
    }

    private record Session(String access) {}
}

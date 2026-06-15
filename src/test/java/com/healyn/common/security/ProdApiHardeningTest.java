package com.healyn.common.security;

import com.healyn.appointments.repository.AppointmentEventRepository;
import com.healyn.appointments.repository.AppointmentRepository;
import com.healyn.audit.repository.AuditLogRepository;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.repository.DeviceSessionRepository;
import com.healyn.auth.repository.OtpChallengeRepository;
import com.healyn.availability.repository.AvailabilityRuleRepository;
import com.healyn.availability.repository.BlackoutWindowRepository;
import com.healyn.compliance.repository.AccountDeletionRequestRepository;
import com.healyn.compliance.repository.ConsentRecordRepository;
import com.healyn.compliance.repository.LegalDocumentRepository;
import com.healyn.discussion.repository.DiscussionMessageAttachmentRepository;
import com.healyn.discussion.repository.DiscussionMessageRepository;
import com.healyn.discussion.repository.DiscussionReadMarkerRepository;
import com.healyn.files.repository.FileObjectRepository;
import com.healyn.notifications.repository.FcmTokenRepository;
import com.healyn.notifications.repository.NotificationOutboxRepository;
import com.healyn.notifications.repository.NotificationPreferencesRepository;
import com.healyn.patients.repository.AccountAddressRepository;
import com.healyn.patients.repository.AccountPatientRepository;
import com.healyn.patients.repository.PatientRepository;
import com.healyn.physio.repository.PhysioProfileRepository;
import com.healyn.promotions.repository.PromotionRepository;
import com.healyn.treatmentnotes.repository.TreatmentNoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the production API hardening (audit §11 item 10 / finding S-2): with the {@code prod}
 * profile active, the OpenAPI spec and Swagger UI are not served, and Actuator metrics are not on
 * the public HTTP port.
 *
 * <p>Boots the full web + security context without Docker by excluding the DB/Redis auto-config and
 * mocking the repositories (same approach as {@code ApplicationBootSmokeTest}). The {@code test}
 * profile is kept active alongside {@code prod} so the prod fail-fast guards (JWT keys / FCM) accept
 * the ephemeral test setup; {@code prod} wins for the overlapping springdoc/management keys.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        // Keep Actuator on the single random web port for the test instead of prod's
        // separate management port, so there is no fixed-port collision in CI.
        "management.server.port=-1",
        "healyn.compliance.poller-enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles({"test", "prod"})
class ProdApiHardeningTest {

    @MockBean AccountRepository accountRepository;
    @MockBean DeviceSessionRepository deviceSessionRepository;
    @MockBean OtpChallengeRepository otpChallengeRepository;
    @MockBean PatientRepository patientRepository;
    @MockBean AccountPatientRepository accountPatientRepository;
    @MockBean AccountAddressRepository accountAddressRepository;
    @MockBean AvailabilityRuleRepository availabilityRuleRepository;
    @MockBean BlackoutWindowRepository blackoutWindowRepository;
    @MockBean AppointmentRepository appointmentRepository;
    @MockBean AppointmentEventRepository appointmentEventRepository;
    @MockBean DiscussionMessageRepository discussionMessageRepository;
    @MockBean DiscussionReadMarkerRepository discussionReadMarkerRepository;
    @MockBean DiscussionMessageAttachmentRepository discussionMessageAttachmentRepository;
    @MockBean FileObjectRepository fileObjectRepository;
    @MockBean TreatmentNoteRepository treatmentNoteRepository;
    @MockBean PhysioProfileRepository physioProfileRepository;
    @MockBean PromotionRepository promotionRepository;
    @MockBean NotificationOutboxRepository notificationOutboxRepository;
    @MockBean FcmTokenRepository fcmTokenRepository;
    @MockBean NotificationPreferencesRepository notificationPreferencesRepository;
    @MockBean ConsentRecordRepository consentRecordRepository;
    @MockBean AccountDeletionRequestRepository accountDeletionRequestRepository;
    @MockBean LegalDocumentRepository legalDocumentRepository;
    @MockBean AuditLogRepository auditLogRepository;
    @MockBean StringRedisTemplate stringRedisTemplate;
    @MockBean JdbcTemplate jdbcTemplate;

    @Autowired TestRestTemplate rest;

    @Test
    void openApiSpecIsNotServedInProd() {
        ResponseEntity<String> r = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(r.getStatusCode().is2xxSuccessful())
                .as("OpenAPI spec must not be reachable in prod")
                .isFalse();
    }

    @Test
    void swaggerUiIsNotServedInProd() {
        ResponseEntity<String> r = rest.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(r.getStatusCode().is2xxSuccessful())
                .as("Swagger UI must not be reachable in prod")
                .isFalse();
    }

    @Test
    void actuatorMetricsAreNotOnThePublicPort() {
        ResponseEntity<String> r = rest.getForEntity("/actuator/prometheus", String.class);
        assertThat(r.getStatusCode().is2xxSuccessful())
                .as("Actuator metrics must not be exposed on the public HTTP port in prod")
                .isFalse();
    }
}

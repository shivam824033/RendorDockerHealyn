package com.healyn;

import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.repository.DeviceSessionRepository;
import com.healyn.auth.repository.OtpChallengeRepository;
import com.healyn.appointments.repository.AppointmentRepository;
import com.healyn.audit.repository.AuditLogRepository;
import com.healyn.availability.repository.AvailabilityRuleRepository;
import com.healyn.availability.repository.BlackoutWindowRepository;
import com.healyn.discussion.repository.DiscussionMessageAttachmentRepository;
import com.healyn.discussion.repository.DiscussionMessageRepository;
import com.healyn.discussion.repository.DiscussionReadMarkerRepository;
import com.healyn.files.repository.FileObjectRepository;
import com.healyn.notifications.repository.FcmTokenRepository;
import com.healyn.notifications.repository.NotificationOutboxRepository;
import com.healyn.notifications.repository.NotificationPreferencesRepository;
import com.healyn.patients.repository.AccountPatientRepository;
import com.healyn.patients.repository.PatientRepository;
import com.healyn.treatmentnotes.repository.TreatmentNoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ActiveProfiles("test")
class ApplicationBootSmokeTest {

    @MockBean AccountRepository accountRepository;
    @MockBean DeviceSessionRepository deviceSessionRepository;
    @MockBean OtpChallengeRepository otpChallengeRepository;
    @MockBean PatientRepository patientRepository;
    @MockBean AccountPatientRepository accountPatientRepository;
    @MockBean AvailabilityRuleRepository availabilityRuleRepository;
    @MockBean BlackoutWindowRepository blackoutWindowRepository;
    @MockBean AppointmentRepository appointmentRepository;
    @MockBean DiscussionMessageRepository discussionMessageRepository;
    @MockBean DiscussionReadMarkerRepository discussionReadMarkerRepository;
    @MockBean DiscussionMessageAttachmentRepository discussionMessageAttachmentRepository;
    @MockBean FileObjectRepository fileObjectRepository;
    @MockBean TreatmentNoteRepository treatmentNoteRepository;
    @MockBean NotificationOutboxRepository notificationOutboxRepository;
    @MockBean FcmTokenRepository fcmTokenRepository;
    @MockBean NotificationPreferencesRepository notificationPreferencesRepository;
    @MockBean AuditLogRepository auditLogRepository;
    @MockBean StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
    }
}

package com.healyn.notifications;

import com.healyn.notifications.domain.NotificationOutbox;
import com.healyn.notifications.domain.NotificationStatus;
import com.healyn.notifications.repository.FcmTokenRepository;
import com.healyn.notifications.repository.NotificationOutboxRepository;
import com.healyn.notifications.service.OutboxDispatcher;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OutboxDispatcherIntegrationTest {

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

    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired NotificationOutboxRepository outbox;
    @Autowired FcmTokenRepository tokens;

    @Test
    void due_row_with_a_live_token_is_delivered_and_marked_sent() {
        UUID account = seedAccount();
        seedToken(account, "tok-live-" + UUID.randomUUID());
        UUID rowId = seedDueOutboxRow(account);

        int processed = dispatcher.dispatchDue();

        assertThat(processed).isGreaterThanOrEqualTo(1);
        NotificationOutbox row = outbox.findById(rowId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.getTargetFcmToken()).startsWith("tok-live-");
        assertThat(row.getSentAt()).isNotNull();
    }

    @Test
    void due_row_with_no_tokens_is_terminal_sent() {
        UUID account = seedAccount();
        UUID rowId = seedDueOutboxRow(account);

        dispatcher.dispatchDue();

        NotificationOutbox row = outbox.findById(rowId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.getTargetFcmToken()).isNull();
    }

    private UUID seedAccount() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts(id, email, password_hash, password_salt, role, status) "
                        + "values (?, ?, 'x', '\\x00'::bytea, 'ROLE_ACCOUNT'::account_role, 'ACTIVE'::account_status)",
                id, "disp+" + id + "@example.com");
        return id;
    }

    private void seedToken(UUID account, String token) {
        tokens.saveAndFlush(new com.healyn.notifications.domain.FcmToken(
                UUID.randomUUID(), account, token, "android", "dev-1"));
    }

    private UUID seedDueOutboxRow(UUID account) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into notification_outbox(id, kind, target_account_id, payload, next_attempt_at) "
                        + "values (?, 'BOOKING_CONFIRMED'::notification_kind, ?, '{\"appointmentId\":\"a1\"}'::jsonb, now() - interval '1 minute')",
                id, account);
        return id;
    }
}

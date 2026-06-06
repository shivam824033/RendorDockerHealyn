package com.healyn.audit;

import com.healyn.audit.domain.AuditAction;
import com.healyn.audit.domain.AuditLogEntry;
import com.healyn.audit.domain.AuditResource;
import com.healyn.audit.repository.AuditLogRepository;
import com.healyn.audit.service.AuditLogger;
import com.healyn.auth.domain.AccountRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLoggerTest {

    private static final Instant NOW = Instant.parse("2026-06-02T09:00:00Z");

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogger logger = new AuditLogger(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void record_persists_entry_with_supplied_fields() {
        UUID actor = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        logger.record(AuditAction.DOWNLOAD, actor, AccountRole.ROLE_ACCOUNT,
                AuditResource.FILE, fileId, Map.of("patientId", patientId.toString()));

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository).save(captor.capture());
        AuditLogEntry entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo(AuditAction.DOWNLOAD);
        assertThat(entry.getActorAccountId()).isEqualTo(actor);
        assertThat(entry.getActorRole()).isEqualTo(AccountRole.ROLE_ACCOUNT);
        assertThat(entry.getResourceType()).isEqualTo(AuditResource.FILE);
        assertThat(entry.getResourceId()).isEqualTo(fileId);
        assertThat(entry.getOccurredAt()).isEqualTo(NOW);
        assertThat(entry.getMetadata()).containsEntry("patientId", patientId.toString());
    }

    @Test
    void record_allows_null_metadata() {
        UUID actor = UUID.randomUUID();
        UUID apptId = UUID.randomUUID();

        logger.record(AuditAction.CREATE, actor, AccountRole.ROLE_PHYSIO,
                AuditResource.APPOINTMENT, apptId, null);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isNull();
    }
}

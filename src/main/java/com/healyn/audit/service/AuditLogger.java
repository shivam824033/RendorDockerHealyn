package com.healyn.audit.service;

import com.healyn.audit.domain.AuditAction;
import com.healyn.audit.domain.AuditLogEntry;
import com.healyn.audit.repository.AuditLogRepository;
import com.healyn.auth.domain.AccountRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes append-only audit rows. Each call runs in its own transaction
 * ({@link Propagation#REQUIRES_NEW}) so the record persists independently of the
 * caller — it works from read-only contexts (e.g. file download) and is not rolled
 * back if the surrounding action later fails. Metadata carries IDs only, never PHI.
 */
@Service
public class AuditLogger {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditLogger(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, UUID actorAccountId, AccountRole actorRole,
                       String resourceType, UUID resourceId, Map<String, String> metadata) {
        repository.save(new AuditLogEntry(
                action, actorAccountId, actorRole, resourceType, resourceId, metadata, Instant.now(clock)));
    }
}

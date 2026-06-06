package com.healyn.audit.domain;

import com.healyn.auth.domain.AccountRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only clinical access record. Lives in the {@code audit} schema; the app role
 * holds INSERT/SELECT only. Carries IDs and metadata — never PHI (CLAUDE.md Hard Rule #3).
 * {@code request_id} / {@code ip_address} columns exist for a future web-layer enricher
 * and are unmapped in Phase 1.
 */
@Entity
@Table(name = "audit_log", schema = "audit")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_account_id", updatable = false)
    private UUID actorAccountId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "actor_role", updatable = false, columnDefinition = "account_role")
    private AccountRole actorRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "action", nullable = false, updatable = false, columnDefinition = "audit_action")
    private AuditAction action;

    @Column(name = "resource_type", nullable = false, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", updatable = false, columnDefinition = "jsonb")
    private Map<String, String> metadata;

    protected AuditLogEntry() {}

    public AuditLogEntry(AuditAction action,
                         UUID actorAccountId,
                         AccountRole actorRole,
                         String resourceType,
                         UUID resourceId,
                         Map<String, String> metadata,
                         Instant occurredAt) {
        this.action = action;
        this.actorAccountId = actorAccountId;
        this.actorRole = actorRole;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.metadata = metadata == null ? null : Map.copyOf(metadata);
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getActorAccountId() { return actorAccountId; }
    public AccountRole getActorRole() { return actorRole; }
    public AuditAction getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public Map<String, String> getMetadata() { return metadata; }
}

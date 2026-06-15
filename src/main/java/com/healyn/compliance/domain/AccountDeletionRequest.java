package com.healyn.compliance.domain;

import com.healyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/// A right-to-erasure request. After {@code requestedAt} the holder has a cancellable grace
/// window ending at {@code purgeAfter}; once it elapses the scheduled job anonymizes the
/// account and redacts patient identity PII (clinical data retained — Hard Rule #7).
@Entity
@Table(name = "account_deletion_requests")
public class AccountDeletionRequest extends BaseEntity {

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "deletion_request_status")
    private DeletionRequestStatus status = DeletionRequestStatus.REQUESTED;

    @Column(name = "reason")
    private String reason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "purge_after", nullable = false)
    private Instant purgeAfter;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @Column(name = "purged_at")
    private Instant purgedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected AccountDeletionRequest() {}

    public AccountDeletionRequest(UUID id, UUID accountId, String reason,
                                  Instant requestedAt, Instant purgeAfter) {
        this.id = id;
        this.accountId = accountId;
        this.reason = reason;
        this.requestedAt = requestedAt;
        this.purgeAfter = purgeAfter;
    }

    public void cancel(Instant when) {
        this.status = DeletionRequestStatus.CANCELLED;
        this.cancelledAt = when;
    }

    public void markAnonymized(Instant when) {
        this.status = DeletionRequestStatus.ANONYMIZED;
        this.anonymizedAt = when;
    }

    public void markPurged(Instant when) {
        this.status = DeletionRequestStatus.PURGED;
        this.purgedAt = when;
    }

    public UUID getAccountId() { return accountId; }
    public DeletionRequestStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getPurgeAfter() { return purgeAfter; }
    public Instant getAnonymizedAt() { return anonymizedAt; }
    public Instant getPurgedAt() { return purgedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}

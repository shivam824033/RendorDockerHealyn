package com.healyn.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One outbound notification per recipient. Written in the same transaction as the
 * domain action that triggers it; a separate poller dispatches PENDING rows.
 * The payload carries IDs only — never PHI (CLAUDE.md Hard Rule #4).
 */
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, updatable = false, columnDefinition = "notification_kind")
    private NotificationKind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "channel", nullable = false, columnDefinition = "notification_channel")
    private NotificationChannel channel;

    @Column(name = "target_account_id", nullable = false, updatable = false)
    private UUID targetAccountId;

    @Column(name = "target_fcm_token")
    private String targetFcmToken;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, String> payload;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "notification_status")
    private NotificationStatus status;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "correlation_id", updatable = false)
    private UUID correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationOutbox() {}

    public NotificationOutbox(UUID id,
                              NotificationKind kind,
                              UUID targetAccountId,
                              Map<String, String> payload,
                              UUID correlationId,
                              Instant now) {
        this.id = id;
        this.kind = kind;
        this.channel = NotificationChannel.FCM;
        this.targetAccountId = targetAccountId;
        this.payload = Map.copyOf(payload);
        this.correlationId = correlationId;
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public NotificationKind getKind() { return kind; }
    public NotificationChannel getChannel() { return channel; }
    public UUID getTargetAccountId() { return targetAccountId; }
    public String getTargetFcmToken() { return targetFcmToken; }
    public Map<String, String> getPayload() { return payload; }
    public NotificationStatus getStatus() { return status; }
    public short getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public String getLastError() { return lastError; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Reserve this row for an in-flight dispatch by pushing {@code nextAttemptAt} forward,
     * so a concurrent poller (another instance) skips it while delivery is attempted outside
     * the DB transaction. Does not change status or count an attempt; if the dispatcher dies
     * mid-flight the lease expires and the row is retried. The final outcome
     * ({@link #markSent}/{@link #reschedule}/{@link #markDead}) overwrites the lease.
     */
    public void leaseUntil(Instant until) {
        this.nextAttemptAt = until;
    }

    /** Delivered (or terminally not deliverable, e.g. no live tokens). */
    public void markSent(Instant now, String resolvedToken) {
        this.attempts = (short) (this.attempts + 1);
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.targetFcmToken = resolvedToken;
        this.lastError = null;
    }

    /** Transient failure with retries remaining: stay PENDING, back off to {@code nextAttemptAt}. */
    public void reschedule(Instant nextAttemptAt, String error) {
        this.attempts = (short) (this.attempts + 1);
        this.status = NotificationStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = truncate(error);
    }

    /** Retries exhausted: give up. */
    public void markDead(String error) {
        this.attempts = (short) (this.attempts + 1);
        this.status = NotificationStatus.DEAD;
        this.lastError = truncate(error);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}

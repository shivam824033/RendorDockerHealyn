package com.healyn.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "target", nullable = false)
    private String target;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "channel", nullable = false, columnDefinition = "otp_channel")
    private OtpChannel channel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "purpose", nullable = false, columnDefinition = "otp_purpose")
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false)
    private byte[] codeHash;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpChallenge() {}

    public OtpChallenge(UUID id, UUID accountId, String target, OtpChannel channel,
                        OtpPurpose purpose, byte[] codeHash, Instant expiresAt) {
        this.id = id;
        this.accountId = accountId;
        this.target = target;
        this.channel = channel;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public String getTarget() { return target; }
    public OtpChannel getChannel() { return channel; }
    public OtpPurpose getPurpose() { return purpose; }
    public byte[] getCodeHash() { return codeHash; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isExhausted() { return attempts >= maxAttempts; }
    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
    public boolean isConsumed() { return consumedAt != null; }

    public void recordAttempt() { this.attempts += 1; }
    public void consume() { this.consumedAt = Instant.now(); }
}

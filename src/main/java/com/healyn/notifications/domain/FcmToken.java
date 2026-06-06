package com.healyn.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A device's FCM registration token. One row per app install; {@code token} is unique
 * while live. The outbox dispatcher resolves an account's live tokens at send time and
 * retires any that FCM reports as unregistered. Owned by the notifications module
 * (SYSTEM_ARCHITECTURE §3.1); registered via {@code POST /auth/fcm_tokens}.
 */
@Entity
@Table(name = "fcm_tokens")
public class FcmToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token", nullable = false, updatable = false)
    private String token;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected FcmToken() {}

    public FcmToken(UUID id, UUID accountId, String token, String platform, String deviceId) {
        this.id = id;
        this.accountId = accountId;
        this.token = token;
        this.platform = platform;
        this.deviceId = deviceId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
    }

    /** Re-registration of an existing token: bind it to the current account and refresh metadata. */
    public void reassignTo(UUID accountId, String platform, String deviceId) {
        this.accountId = accountId;
        this.platform = platform;
        this.deviceId = deviceId;
        this.lastSeenAt = Instant.now();
    }

    /** FCM reported the token as unregistered; retire it so the dispatcher stops resolving it. */
    public void retire() {
        if (this.deletedAt == null) this.deletedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public String getToken() { return token; }
    public String getPlatform() { return platform; }
    public String getDeviceId() { return deviceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getDeletedAt() { return deletedAt; }
}

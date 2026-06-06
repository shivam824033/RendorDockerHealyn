package com.healyn.auth.domain;

import com.healyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_sessions")
public class DeviceSession extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "refresh_token_hash", nullable = false)
    private byte[] refreshTokenHash;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "device_label")
    private String deviceLabel;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected DeviceSession() {}

    public DeviceSession(UUID id, UUID accountId, byte[] refreshTokenHash,
                         String deviceId, String deviceLabel, String fcmToken,
                         String ipAddress, String userAgent, Instant expiresAt) {
        this.id = id;
        this.accountId = accountId;
        this.refreshTokenHash = refreshTokenHash;
        this.deviceId = deviceId;
        this.deviceLabel = deviceLabel;
        this.fcmToken = fcmToken;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        Instant now = Instant.now();
        this.issuedAt = now;
        this.lastSeenAt = now;
        this.expiresAt = expiresAt;
    }

    public UUID getAccountId() { return accountId; }
    public byte[] getRefreshTokenHash() { return refreshTokenHash; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceLabel() { return deviceLabel; }
    public String getFcmToken() { return fcmToken; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void rotate(byte[] newRefreshHash, Instant newExpiresAt, String ip, String userAgent) {
        this.refreshTokenHash = newRefreshHash;
        this.expiresAt = newExpiresAt;
        this.lastSeenAt = Instant.now();
        this.ipAddress = ip;
        this.userAgent = userAgent;
    }

    public void touch(String ip, String userAgent) {
        this.lastSeenAt = Instant.now();
        if (ip != null) this.ipAddress = ip;
        if (userAgent != null) this.userAgent = userAgent;
    }

    public void revoke() {
        if (this.revokedAt == null) this.revokedAt = Instant.now();
    }
}

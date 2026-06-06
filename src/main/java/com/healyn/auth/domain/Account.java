package com.healyn.auth.domain;

import com.healyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    @Column(name = "email", columnDefinition = "citext")
    private String email;

    @Column(name = "phone_e164")
    private String phoneE164;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_salt", nullable = false)
    private byte[] passwordSalt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "account_role")
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "account_status")
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Account() {}

    public Account(java.util.UUID id, String email, String phoneE164,
                   String passwordHash, byte[] passwordSalt, AccountRole role) {
        this.id = id;
        this.email = email;
        this.phoneE164 = phoneE164;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.role = role;
    }

    public String getEmail() { return email; }
    public String getPhoneE164() { return phoneE164; }
    public String getPasswordHash() { return passwordHash; }
    public byte[] getPasswordSalt() { return passwordSalt; }
    public AccountRole getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void replacePassword(String newHash, byte[] newSalt) {
        this.passwordHash = newHash;
        this.passwordSalt = newSalt;
    }

    public void recordFailedLogin() {
        this.failedLoginCount += 1;
    }

    public void lock(Instant until) {
        this.lockedUntil = until;
        this.status = AccountStatus.LOCKED;
        this.failedLoginCount = 0;
    }

    public void unlock() {
        this.lockedUntil = null;
        this.status = AccountStatus.ACTIVE;
    }

    public void recordSuccessfulLogin(Instant when) {
        this.failedLoginCount = 0;
        this.lastLoginAt = when;
        this.lockedUntil = null;
        if (this.status == AccountStatus.LOCKED) this.status = AccountStatus.ACTIVE;
    }
}

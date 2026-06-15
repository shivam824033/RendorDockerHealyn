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

    /// Marks the account as having a deletion request pending in its grace window. It can
    /// still authenticate so the holder can cancel; {@link #anonymize} ends the lifecycle.
    public void markPendingDeletion() {
        this.status = AccountStatus.PENDING_DELETION;
    }

    /// Reverts a pending deletion when the holder cancels within the grace window.
    public void cancelPendingDeletion() {
        if (this.status == AccountStatus.PENDING_DELETION) this.status = AccountStatus.ACTIVE;
    }

    /// Right-to-erasure: strips identifying credentials and contact details and disables the
    /// account. The phone is cleared and the email is replaced by a non-identifying, unique
    /// tombstone derived from the account id — the {@code accounts_email_or_phone} check
    /// requires one contact column to be non-null, and the tombstone keeps the unique index
    /// satisfied without retaining the real address. The password hash is overwritten with an
    /// unusable random value. Idempotent — re-running on an already-anonymized account is a no-op.
    public void anonymize(String unusableHash, byte[] unusableSalt, Instant when) {
        this.email = "deleted-" + this.id + "@anonymized.invalid";
        this.phoneE164 = null;
        this.passwordHash = unusableHash;
        this.passwordSalt = unusableSalt;
        this.status = AccountStatus.DISABLED;
        this.lockedUntil = null;
        this.lastLoginAt = null;
        if (this.deletedAt == null) this.deletedAt = when;
    }
}

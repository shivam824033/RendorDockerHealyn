package com.healyn.patients.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/// The single postal address of an Account's household. Keyed by {@code accountId}
/// (1:1 with {@code accounts}), captured at registration and shared across the
/// account's primary patient and every family member. Account contact data, not
/// clinical PHI in the audit sense — no soft-delete (mirrors notification
/// preferences). See docs/DATABASE_SCHEMA.md §3.5a.
@Entity
@Table(name = "account_addresses")
public class AccountAddress {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "line1", nullable = false, length = 160)
    private String line1;

    @Column(name = "line2", length = 160)
    private String line2;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "state", nullable = false, length = 80)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 16)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 60)
    private String country = "India";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountAddress() {}

    public AccountAddress(UUID accountId, String line1, String line2, String city,
                          String state, String postalCode, String country) {
        this.accountId = accountId;
        apply(line1, line2, city, state, postalCode, country);
    }

    /// Overwrites every field from a new address. Used by both first-time create
    /// and edit, so a household that moves clears stale values rather than merging.
    public void apply(String line1, String line2, String city,
                      String state, String postalCode, String country) {
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        if (country != null && !country.isBlank()) this.country = country;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getAccountId() { return accountId; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

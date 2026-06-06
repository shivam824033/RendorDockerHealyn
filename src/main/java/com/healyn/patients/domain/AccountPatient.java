package com.healyn.patients.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_patients")
public class AccountPatient {

    @EmbeddedId
    private AccountPatientId id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "relationship", nullable = false, columnDefinition = "patient_relationship")
    private PatientRelationship relationship;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "can_manage", nullable = false)
    private boolean canManage = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountPatient() {}

    public AccountPatient(UUID accountId, UUID patientId, PatientRelationship relationship,
                          boolean primary, boolean canManage) {
        this.id = new AccountPatientId(accountId, patientId);
        this.relationship = relationship;
        this.primary = primary;
        this.canManage = canManage;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public AccountPatientId getId() { return id; }
    public UUID getAccountId() { return id.getAccountId(); }
    public UUID getPatientId() { return id.getPatientId(); }
    public PatientRelationship getRelationship() { return relationship; }
    public boolean isPrimary() { return primary; }
    public boolean isCanManage() { return canManage; }
    public Instant getCreatedAt() { return createdAt; }
}

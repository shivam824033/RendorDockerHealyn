package com.healyn.patients.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AccountPatientId implements Serializable {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    protected AccountPatientId() {}

    public AccountPatientId(UUID accountId, UUID patientId) {
        this.accountId = accountId;
        this.patientId = patientId;
    }

    public UUID getAccountId() { return accountId; }
    public UUID getPatientId() { return patientId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountPatientId that)) return false;
        return Objects.equals(accountId, that.accountId) && Objects.equals(patientId, that.patientId);
    }

    @Override
    public int hashCode() { return Objects.hash(accountId, patientId); }
}

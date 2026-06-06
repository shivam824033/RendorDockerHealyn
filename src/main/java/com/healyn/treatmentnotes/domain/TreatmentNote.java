package com.healyn.treatmentnotes.domain;

import com.healyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "treatment_notes")
public class TreatmentNote extends BaseEntity {

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "author_account_id", nullable = false, updatable = false)
    private UUID authorAccountId;

    @Column(name = "diagnosis")
    private String diagnosis;

    @Column(name = "notes")
    private String notes;

    @Column(name = "recovery_instructions")
    private String recoveryInstructions;

    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected TreatmentNote() {}

    public TreatmentNote(UUID id,
                         UUID appointmentId,
                         UUID patientId,
                         UUID authorAccountId,
                         String diagnosis,
                         String notes,
                         String recoveryInstructions,
                         Instant nextReviewAt) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.authorAccountId = authorAccountId;
        this.diagnosis = diagnosis;
        this.notes = notes;
        this.recoveryInstructions = recoveryInstructions;
        this.nextReviewAt = nextReviewAt;
    }

    public UUID getAppointmentId() { return appointmentId; }
    public UUID getPatientId() { return patientId; }
    public UUID getAuthorAccountId() { return authorAccountId; }
    public String getDiagnosis() { return diagnosis; }
    public String getNotes() { return notes; }
    public String getRecoveryInstructions() { return recoveryInstructions; }
    public Instant getNextReviewAt() { return nextReviewAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void revise(String diagnosis, String notes, String recoveryInstructions, Instant nextReviewAt) {
        this.diagnosis = diagnosis;
        this.notes = notes;
        this.recoveryInstructions = recoveryInstructions;
        this.nextReviewAt = nextReviewAt;
    }
}

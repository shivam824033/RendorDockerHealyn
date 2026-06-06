package com.healyn.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;
import java.util.UUID;

/**
 * An account's per-category push opt-outs. One row per account; every category defaults to
 * opted-in. A row only exists once the account has changed a default — {@code defaultsFor}
 * builds the transient all-enabled view the GET endpoint returns when none is stored yet.
 * Config, not clinical data: no soft-delete (CLAUDE.md Hard Rule #7 does not apply).
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreferences {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "appointment_updates", nullable = false)
    private boolean appointmentUpdates = true;

    @Column(name = "appointment_reminders", nullable = false)
    private boolean appointmentReminders = true;

    @Column(name = "messages", nullable = false)
    private boolean messages = true;

    @Column(name = "treatment_notes", nullable = false)
    private boolean treatmentNotes = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreferences() {}

    private NotificationPreferences(UUID accountId) {
        this.accountId = accountId;
    }

    /** A transient, all-enabled view for an account with no stored row. Not persisted by reads. */
    public static NotificationPreferences defaultsFor(UUID accountId) {
        return new NotificationPreferences(accountId);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Apply a partial update: a {@code null} leaves that category unchanged (WYSIWYG PATCH). */
    public void apply(Boolean appointmentUpdates, Boolean appointmentReminders,
                      Boolean messages, Boolean treatmentNotes) {
        if (appointmentUpdates != null) this.appointmentUpdates = appointmentUpdates;
        if (appointmentReminders != null) this.appointmentReminders = appointmentReminders;
        if (messages != null) this.messages = messages;
        if (treatmentNotes != null) this.treatmentNotes = treatmentNotes;
    }

    @Transient
    public boolean enabledFor(NotificationCategory category) {
        return switch (category) {
            case APPOINTMENT_UPDATES -> appointmentUpdates;
            case APPOINTMENT_REMINDERS -> appointmentReminders;
            case MESSAGES -> messages;
            case TREATMENT_NOTES -> treatmentNotes;
        };
    }

    public UUID getAccountId() { return accountId; }
    public boolean isAppointmentUpdates() { return appointmentUpdates; }
    public boolean isAppointmentReminders() { return appointmentReminders; }
    public boolean isMessages() { return messages; }
    public boolean isTreatmentNotes() { return treatmentNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

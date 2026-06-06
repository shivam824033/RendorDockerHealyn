package com.healyn.discussion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discussion_messages")
public class DiscussionMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Column(name = "sender_account_id", nullable = false, updatable = false)
    private UUID senderAccountId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "sender_role", nullable = false, updatable = false, columnDefinition = "discussion_sender_role")
    private DiscussionSenderRole senderRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "message_type", nullable = false, updatable = false, columnDefinition = "discussion_message_type")
    private DiscussionMessageType messageType;

    @Column(name = "body")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected DiscussionMessage() {}

    public DiscussionMessage(UUID id,
                             UUID appointmentId,
                             UUID senderAccountId,
                             DiscussionSenderRole senderRole,
                             DiscussionMessageType messageType,
                             String body) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.senderAccountId = senderAccountId;
        this.senderRole = senderRole;
        this.messageType = messageType;
        this.body = body;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getSenderAccountId() { return senderAccountId; }
    public DiscussionSenderRole getSenderRole() { return senderRole; }
    public DiscussionMessageType getMessageType() { return messageType; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void edit(String newBody, Instant now) {
        this.body = newBody;
        this.editedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }
}

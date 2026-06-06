package com.healyn.discussion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "discussion_read_markers")
public class DiscussionReadMarker {

    @EmbeddedId
    private Key key;

    @Column(name = "last_read_message_id", nullable = false)
    private UUID lastReadMessageId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DiscussionReadMarker() {}

    public DiscussionReadMarker(UUID appointmentId, UUID accountId, UUID lastReadMessageId) {
        this.key = new Key(appointmentId, accountId);
        this.lastReadMessageId = lastReadMessageId;
    }

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = Instant.now();
    }

    public Key getKey() { return key; }
    public UUID getAppointmentId() { return key.appointmentId; }
    public UUID getAccountId() { return key.accountId; }
    public UUID getLastReadMessageId() { return lastReadMessageId; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void advanceTo(UUID messageId) {
        this.lastReadMessageId = messageId;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "appointment_id", nullable = false, updatable = false)
        private UUID appointmentId;

        @Column(name = "account_id", nullable = false, updatable = false)
        private UUID accountId;

        protected Key() {}

        public Key(UUID appointmentId, UUID accountId) {
            this.appointmentId = appointmentId;
            this.accountId = accountId;
        }

        public UUID getAppointmentId() { return appointmentId; }
        public UUID getAccountId() { return accountId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(appointmentId, k.appointmentId) && Objects.equals(accountId, k.accountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(appointmentId, accountId);
        }
    }
}

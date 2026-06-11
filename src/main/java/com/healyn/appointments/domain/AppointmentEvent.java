package com.healyn.appointments.domain;

import com.healyn.auth.domain.AccountRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry on an appointment's append-only timeline (APPOINTMENT_FLOW §3). Insert-only —
 * every column is {@code updatable = false} and nothing ever deletes a row; visibility
 * follows the (soft-deletable) appointment. PHI-free by construction: IDs, enums and
 * timestamps only — free text (reason, cancel note) stays on the appointments row, so an
 * event is safe anywhere an ID is (CLAUDE.md hard rules #3, #4).
 */
@Entity
@Table(name = "appointment_events")
public class AppointmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "event_type", nullable = false, updatable = false,
            columnDefinition = "appointment_event_type")
    private AppointmentEventType eventType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_account_id", updatable = false)
    private UUID actorAccountId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "actor_role", updatable = false, columnDefinition = "account_role")
    private AccountRole actorRole;

    /// The other side of a parent-child action: for RESCHEDULED the replacement row,
    /// for a child's CREATED the source it derived from. Null on plain lifecycle events.
    @Column(name = "related_appointment_id", updatable = false)
    private UUID relatedAppointmentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "child_kind", updatable = false, columnDefinition = "appointment_child_kind")
    private AppointmentChildKind childKind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "cancel_reason", updatable = false, columnDefinition = "appointment_cancel_reason")
    private AppointmentCancelReason cancelReason;

    protected AppointmentEvent() {}

    public AppointmentEvent(UUID appointmentId,
                            AppointmentEventType eventType,
                            Instant occurredAt,
                            UUID actorAccountId,
                            AccountRole actorRole,
                            UUID relatedAppointmentId,
                            AppointmentChildKind childKind,
                            AppointmentCancelReason cancelReason) {
        this.appointmentId = appointmentId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.actorAccountId = actorAccountId;
        this.actorRole = actorRole;
        this.relatedAppointmentId = relatedAppointmentId;
        this.childKind = childKind;
        this.cancelReason = cancelReason;
    }

    public Long getId() { return id; }
    public UUID getAppointmentId() { return appointmentId; }
    public AppointmentEventType getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getActorAccountId() { return actorAccountId; }
    public AccountRole getActorRole() { return actorRole; }
    public UUID getRelatedAppointmentId() { return relatedAppointmentId; }
    public AppointmentChildKind getChildKind() { return childKind; }
    public AppointmentCancelReason getCancelReason() { return cancelReason; }
}

package com.healyn.availability.domain;

import com.healyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blackout_windows")
public class BlackoutWindow extends BaseEntity {

    @Column(name = "physiotherapist_id", nullable = false, updatable = false)
    private UUID physiotherapistId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "reason", length = 200)
    private String reason;

    protected BlackoutWindow() {}

    public BlackoutWindow(UUID id, UUID physiotherapistId, Instant startsAt, Instant endsAt, String reason) {
        this.id = id;
        this.physiotherapistId = physiotherapistId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
    }

    public UUID getPhysiotherapistId() { return physiotherapistId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getReason() { return reason; }
}

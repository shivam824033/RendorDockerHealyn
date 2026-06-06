package com.healyn.availability.domain;

import com.healyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "availability_rules")
public class AvailabilityRule extends BaseEntity {

    @Column(name = "physiotherapist_id", nullable = false, updatable = false)
    private UUID physiotherapistId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_minutes", nullable = false)
    private short slotMinutes;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    protected AvailabilityRule() {}

    public AvailabilityRule(UUID id, UUID physiotherapistId, short dayOfWeek,
                            LocalTime startTime, LocalTime endTime, short slotMinutes,
                            String timezone, LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.id = id;
        this.physiotherapistId = physiotherapistId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotMinutes = slotMinutes;
        this.timezone = timezone;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public UUID getPhysiotherapistId() { return physiotherapistId; }
    public short getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public short getSlotMinutes() { return slotMinutes; }
    public String getTimezone() { return timezone; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }

    public void setDayOfWeek(short dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public void setSlotMinutes(short slotMinutes) { this.slotMinutes = slotMinutes; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
}

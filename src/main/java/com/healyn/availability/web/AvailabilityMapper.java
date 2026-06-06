package com.healyn.availability.web;

import com.healyn.availability.domain.AvailabilityRule;
import com.healyn.availability.domain.BlackoutWindow;
import com.healyn.availability.service.Slot;

public final class AvailabilityMapper {

    private AvailabilityMapper() {}

    public static AvailabilityDtos.RuleView toView(AvailabilityRule rule) {
        return new AvailabilityDtos.RuleView(
                rule.getId(),
                rule.getPhysiotherapistId(),
                rule.getDayOfWeek(),
                rule.getStartTime(),
                rule.getEndTime(),
                rule.getSlotMinutes(),
                rule.getTimezone(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo());
    }

    public static AvailabilityDtos.BlackoutView toView(BlackoutWindow blackout) {
        return new AvailabilityDtos.BlackoutView(
                blackout.getId(),
                blackout.getPhysiotherapistId(),
                blackout.getStartsAt(),
                blackout.getEndsAt(),
                blackout.getReason());
    }

    public static AvailabilityDtos.SlotView toView(Slot slot) {
        return new AvailabilityDtos.SlotView(
                slot.startsAt(),
                slot.endsAt(),
                slot.durationMinutes());
    }
}

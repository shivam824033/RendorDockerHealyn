package com.healyn.availability.service;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityRuleUpdate(
        Short dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Short slotMinutes,
        String timezone,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {}

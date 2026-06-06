package com.healyn.availability.service;

import java.time.LocalDate;
import java.time.LocalTime;

public record NewAvailabilityRule(
        short dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        short slotMinutes,
        String timezone,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {}

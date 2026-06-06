package com.healyn.availability.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AvailabilityDtos {

    private AvailabilityDtos() {}

    public record CreateRuleRequest(
            @NotNull @Min(0) @Max(6) Short dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @Min(5) @Max(240) Short slotMinutes,
            @NotBlank @Size(max = 64) String timezone,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    public record UpdateRuleRequest(
            @Min(0) @Max(6) Short dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            @Min(5) @Max(240) Short slotMinutes,
            @Size(max = 64) String timezone,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    public record CreateBlackoutRequest(
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @Size(max = 200) String reason) {}

    public record RuleView(
            UUID id,
            UUID physiotherapistId,
            short dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            short slotMinutes,
            String timezone,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    public record BlackoutView(
            UUID id,
            UUID physiotherapistId,
            Instant startsAt,
            Instant endsAt,
            String reason) {}

    public record SlotView(
            Instant startsAt,
            Instant endsAt,
            int durationMinutes) {}

    public record RuleListResponse(List<RuleView> rules) {}

    public record BlackoutListResponse(List<BlackoutView> blackouts) {}

    public record SlotListResponse(UUID physiotherapistId, List<SlotView> slots) {}
}

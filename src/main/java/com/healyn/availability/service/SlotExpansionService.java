package com.healyn.availability.service;

import com.healyn.availability.domain.AvailabilityRule;
import com.healyn.availability.domain.BlackoutWindow;
import com.healyn.availability.repository.AvailabilityRuleRepository;
import com.healyn.availability.repository.BlackoutWindowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class SlotExpansionService {

    private static final Duration LOOKUP_BUFFER = Duration.ofDays(1);

    private final AvailabilityRuleRepository rules;
    private final BlackoutWindowRepository blackouts;

    public SlotExpansionService(AvailabilityRuleRepository rules, BlackoutWindowRepository blackouts) {
        this.rules = rules;
        this.blackouts = blackouts;
    }

    @Transactional(readOnly = true)
    public List<Slot> expandSlots(UUID physiotherapistId, LocalDate from, LocalDate to,
                                  Collection<TimeRange> bookedRanges) {
        List<AvailabilityRule> ruleList = rules.findActiveByPhysio(physiotherapistId, from, to);
        Instant lookupFrom = from.atStartOfDay(ZoneOffset.UTC).toInstant().minus(LOOKUP_BUFFER);
        Instant lookupTo = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().plus(LOOKUP_BUFFER);
        List<BlackoutWindow> blackoutList = blackouts.findByPhysioOverlapping(physiotherapistId, lookupFrom, lookupTo);
        return expand(physiotherapistId, ruleList, blackoutList, from, to, bookedRanges);
    }

    public static List<Slot> expand(UUID physiotherapistId,
                                    List<AvailabilityRule> rules,
                                    List<BlackoutWindow> blackouts,
                                    LocalDate from, LocalDate to,
                                    Collection<TimeRange> bookedRanges) {
        if (from.isAfter(to)) return List.of();
        List<Slot> out = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            short dow = (short) (date.getDayOfWeek().getValue() % 7);
            for (AvailabilityRule rule : rules) {
                if (rule.getDayOfWeek() != dow) continue;
                if (date.isBefore(rule.getEffectiveFrom())) continue;
                if (rule.getEffectiveTo() != null && date.isAfter(rule.getEffectiveTo())) continue;
                emitSlotsForDay(physiotherapistId, rule, date, blackouts, bookedRanges, out);
            }
        }
        out.sort(Comparator.comparing(Slot::startsAt));
        return out;
    }

    private static void emitSlotsForDay(UUID physiotherapistId, AvailabilityRule rule, LocalDate date,
                                        List<BlackoutWindow> blackouts, Collection<TimeRange> bookedRanges,
                                        List<Slot> out) {
        ZoneId zone = ZoneId.of(rule.getTimezone());
        int slotMinutes = rule.getSlotMinutes();
        ZonedDateTime cursor = date.atTime(rule.getStartTime()).atZone(zone);
        ZonedDateTime windowEnd = date.atTime(rule.getEndTime()).atZone(zone);

        while (true) {
            ZonedDateTime slotEndZdt = cursor.plusMinutes(slotMinutes);
            if (slotEndZdt.isAfter(windowEnd)) break;
            Instant start = cursor.toInstant();
            Instant end = slotEndZdt.toInstant();
            if (!intersectsAnyBlackout(start, end, blackouts)
                    && !intersectsAnyBooked(start, end, bookedRanges)) {
                out.add(new Slot(physiotherapistId, start, end, slotMinutes));
            }
            cursor = slotEndZdt;
        }
    }

    private static boolean intersectsAnyBlackout(Instant start, Instant end, List<BlackoutWindow> blackouts) {
        for (BlackoutWindow b : blackouts) {
            if (b.getStartsAt().isBefore(end) && b.getEndsAt().isAfter(start)) return true;
        }
        return false;
    }

    private static boolean intersectsAnyBooked(Instant start, Instant end, Collection<TimeRange> bookedRanges) {
        if (bookedRanges == null || bookedRanges.isEmpty()) return false;
        for (TimeRange r : bookedRanges) {
            if (r.overlaps(start, end)) return true;
        }
        return false;
    }
}

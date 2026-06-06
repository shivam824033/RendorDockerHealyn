package com.healyn.availability;

import com.healyn.availability.domain.AvailabilityRule;
import com.healyn.availability.domain.BlackoutWindow;
import com.healyn.availability.service.Slot;
import com.healyn.availability.service.SlotExpansionService;
import com.healyn.availability.service.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlotExpansionServiceTest {

    private static final UUID PHYSIO = UUID.randomUUID();
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final ZoneId NY = ZoneId.of("America/New_York");

    @Test
    void simple_rule_expands_to_back_to_back_slots() {
        AvailabilityRule rule = mondayRule(LocalTime.of(9, 0), LocalTime.of(11, 0), (short) 30, IST);
        LocalDate monday = LocalDate.of(2026, 6, 1);

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(rule), List.of(), monday, monday, List.of());

        assertThat(slots).hasSize(4);
        assertThat(slots.get(0).startsAt()).isEqualTo(at(monday, 9, 0, IST));
        assertThat(slots.get(0).endsAt()).isEqualTo(at(monday, 9, 30, IST));
        assertThat(slots.get(3).startsAt()).isEqualTo(at(monday, 10, 30, IST));
        assertThat(slots.get(3).endsAt()).isEqualTo(at(monday, 11, 0, IST));
        for (Slot s : slots) {
            assertThat(Duration.between(s.startsAt(), s.endsAt())).isEqualTo(Duration.ofMinutes(30));
            assertThat(s.durationMinutes()).isEqualTo(30);
        }
    }

    @Test
    void window_that_does_not_evenly_divide_truncates() {
        AvailabilityRule rule = mondayRule(LocalTime.of(9, 0), LocalTime.of(10, 0), (short) 45, IST);
        LocalDate monday = LocalDate.of(2026, 6, 1);

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(rule), List.of(), monday, monday, List.of());

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).startsAt()).isEqualTo(at(monday, 9, 0, IST));
        assertThat(slots.get(0).endsAt()).isEqualTo(at(monday, 9, 45, IST));
    }

    @Test
    void blackout_intersecting_slots_excludes_them() {
        AvailabilityRule rule = mondayRule(LocalTime.of(9, 0), LocalTime.of(11, 0), (short) 30, IST);
        LocalDate monday = LocalDate.of(2026, 6, 1);
        BlackoutWindow blackout = blackout(at(monday, 10, 15, IST), at(monday, 10, 45, IST));

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(rule), List.of(blackout), monday, monday, List.of());

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).startsAt()).isEqualTo(at(monday, 9, 0, IST));
        assertThat(slots.get(1).startsAt()).isEqualTo(at(monday, 9, 30, IST));
    }

    @Test
    void blackout_abutting_slot_does_not_exclude_it() {
        AvailabilityRule rule = mondayRule(LocalTime.of(9, 0), LocalTime.of(11, 0), (short) 30, IST);
        LocalDate monday = LocalDate.of(2026, 6, 1);
        BlackoutWindow blackout = blackout(at(monday, 9, 0, IST), at(monday, 10, 0, IST));

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(rule), List.of(blackout), monday, monday, List.of());

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).startsAt()).isEqualTo(at(monday, 10, 0, IST));
        assertThat(slots.get(1).startsAt()).isEqualTo(at(monday, 10, 30, IST));
    }

    @Test
    void expired_rule_emits_nothing() {
        AvailabilityRule rule = ruleOnDow(
                (short) 1, LocalTime.of(9, 0), LocalTime.of(11, 0), (short) 30, IST,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1));
        LocalDate monday = LocalDate.of(2026, 6, 1);

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(rule), List.of(), monday, monday, List.of());

        assertThat(slots).isEmpty();
    }

    @Test
    void future_rule_emits_nothing_inside_window() {
        AvailabilityRule rule = ruleOnDow(
                (short) 1, LocalTime.of(9, 0), LocalTime.of(11, 0), (short) 30, IST,
                LocalDate.of(2026, 7, 1), null);
        LocalDate monday = LocalDate.of(2026, 6, 1);

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(rule), List.of(), monday, monday, List.of());

        assertThat(slots).isEmpty();
    }

    @Test
    void booked_ranges_subtract_overlapping_slots() {
        AvailabilityRule rule = mondayRule(LocalTime.of(9, 0), LocalTime.of(11, 0), (short) 30, IST);
        LocalDate monday = LocalDate.of(2026, 6, 1);
        TimeRange booked = new TimeRange(at(monday, 9, 30, IST), at(monday, 10, 0, IST));

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(rule), List.of(), monday, monday, List.of(booked));

        assertThat(slots).extracting(Slot::startsAt)
                .containsExactly(
                        at(monday, 9, 0, IST),
                        at(monday, 10, 0, IST),
                        at(monday, 10, 30, IST));
    }

    @Test
    void multi_day_range_only_emits_matching_weekdays() {
        AvailabilityRule mon = mondayRule(LocalTime.of(9, 0), LocalTime.of(10, 0), (short) 30, IST);
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 7);

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(mon), List.of(), from, to, List.of());

        assertThat(slots).hasSize(2);
        assertThat(slots).allSatisfy(s ->
                assertThat(s.startsAt().atZone(IST).toLocalDate()).isEqualTo(from));
    }

    @Test
    void dst_spring_forward_keeps_each_slot_a_real_30_minutes() {
        AvailabilityRule rule = ruleOnDow(
                (short) 0,
                LocalTime.of(1, 0), LocalTime.of(4, 0), (short) 30, NY,
                LocalDate.of(2026, 1, 1), null);
        LocalDate dstSunday = LocalDate.of(2026, 3, 8);

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(rule), List.of(), dstSunday, dstSunday, List.of());

        assertThat(slots).isNotEmpty();
        assertThat(slots).allSatisfy(s ->
                assertThat(Duration.between(s.startsAt(), s.endsAt())).isEqualTo(Duration.ofMinutes(30)));
    }

    @Test
    void from_after_to_emits_nothing() {
        AvailabilityRule rule = mondayRule(LocalTime.of(9, 0), LocalTime.of(10, 0), (short) 30, IST);
        LocalDate from = LocalDate.of(2026, 6, 8);
        LocalDate to = LocalDate.of(2026, 6, 1);

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(rule), List.of(), from, to, List.of());

        assertThat(slots).isEmpty();
    }

    @Test
    void blackout_fully_containing_window_excludes_everything() {
        AvailabilityRule rule = mondayRule(LocalTime.of(9, 0), LocalTime.of(11, 0), (short) 30, IST);
        LocalDate monday = LocalDate.of(2026, 6, 1);
        BlackoutWindow allDay = blackout(at(monday, 0, 0, IST), at(monday.plusDays(1), 0, 0, IST));

        List<Slot> slots = SlotExpansionService.expand(PHYSIO, List.of(rule), List.of(allDay), monday, monday, List.of());

        assertThat(slots).isEmpty();
    }

    private static AvailabilityRule mondayRule(LocalTime start, LocalTime end, short slotMinutes, ZoneId zone) {
        return ruleOnDow((short) 1, start, end, slotMinutes, zone, LocalDate.of(2026, 1, 1), null);
    }

    private static AvailabilityRule ruleOnDow(short dow, LocalTime start, LocalTime end, short slotMinutes,
                                              ZoneId zone, LocalDate effectiveFrom, LocalDate effectiveTo) {
        return new AvailabilityRule(UUID.randomUUID(), PHYSIO, dow, start, end, slotMinutes,
                zone.getId(), effectiveFrom, effectiveTo);
    }

    private static BlackoutWindow blackout(Instant starts, Instant ends) {
        return new BlackoutWindow(UUID.randomUUID(), PHYSIO, starts, ends, null);
    }

    private static Instant at(LocalDate date, int hour, int minute, ZoneId zone) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant();
    }
}

package com.healyn.appointments.service;

import com.healyn.appointments.domain.AppointmentChildKind;
import com.healyn.common.config.ClinicProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/// Generates the human-friendly Appointment Number (PHY-YYYYMMDD-NNNN). The YYYYMMDD stem
/// is the row's *creation* date in the clinic timezone — a request-first row has no
/// scheduled time at creation, so the creation day is the only stable, monotonic basis
/// (APPOINTMENT_FLOW: identifiers). NNNN is a per-day counter held in
/// `appointment_daily_counters` and advanced with a single atomic upsert that RETURNs the
/// new value, so concurrent creations on the same day never collide.
///
/// The upsert runs on the caller's transaction-bound connection (JdbcTemplate shares the
/// Spring-managed connection), so a creation that later rolls back — e.g. a physio-overlap
/// 409 on reschedule/follow-up — reclaims the number rather than leaving a gap.
@Component
public class AppointmentNumberGenerator {

    private static final DateTimeFormatter STEM = DateTimeFormatter.BASIC_ISO_DATE; // YYYYMMDD

    private static final String NEXT_SEQ = """
            INSERT INTO appointment_daily_counters (day, last_seq) VALUES (?, 1)
            ON CONFLICT (day) DO UPDATE SET last_seq = appointment_daily_counters.last_seq + 1
            RETURNING last_seq
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId clinicZone;

    public AppointmentNumberGenerator(JdbcTemplate jdbc, Clock clock, ClinicProperties clinic) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.clinicZone = clinic.zoneId();
    }

    public String generate() {
        LocalDate day = LocalDate.now(clock.withZone(clinicZone));
        Integer seq = jdbc.queryForObject(NEXT_SEQ, Integer.class, Date.valueOf(day));
        return "PHY-" + day.format(STEM) + "-" + String.format("%04d", seq);
    }

    /// Derives a child Appointment Number from its lineage root: the root's number stem
    /// (PHY-YYYYMMDD-NNNN) plus a per-kind suffix, e.g. PHY-20260610-0001 -> -R1 (first
    /// reschedule), -F2 (second follow-up). {@code sourceNumber} is the immediate parent's number;
    /// any existing child suffix on it is dropped so a reschedule of a reschedule still hangs off
    /// the original root stem. {@code existingOfKind} is the count of same-kind children already in
    /// the lineage, so the 1-based ordinal is {@code existingOfKind + 1}. A child never draws from
    /// the per-day counter — its number is fully determined by the root, keeping lineages readable.
    public String childNumber(String sourceNumber, AppointmentChildKind kind, long existingOfKind) {
        return rootStem(sourceNumber) + "-" + kind.suffixLetter() + (existingOfKind + 1);
    }

    /// The first three hyphen segments — PHY-YYYYMMDD-NNNN — shared by every appointment in a
    /// lineage. A root has exactly these; a child appends a suffix segment we strip here.
    private static String rootStem(String number) {
        String[] parts = number.split("-");
        return parts[0] + "-" + parts[1] + "-" + parts[2];
    }
}

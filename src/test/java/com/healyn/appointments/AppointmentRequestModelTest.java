package com.healyn.appointments;

import com.healyn.appointments.domain.Appointment;
import com.healyn.appointments.domain.AppointmentStatus;
import com.healyn.common.id.UuidV7;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/// The request-first appointment model (V15): a request carries a date but no time,
/// and the physiotherapist schedules it later. Pure entity behaviour — no DB needed.
class AppointmentRequestModelTest {

    private static final UUID PATIENT = UuidV7.generate();
    private static final UUID ACCOUNT = UuidV7.generate();
    private static final UUID PHYSIO = UuidV7.generate();

    @Test
    void request_factory_creates_an_unscheduled_requested_appointment() {
        Appointment a = Appointment.request(
                UuidV7.generate(), PATIENT, ACCOUNT, PHYSIO,
                LocalDate.of(2026, 6, 15), LocalTime.of(9, 0), "Lower back pain", null);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.REQUESTED);
        assertThat(a.getScheduledAt()).isNull();
        assertThat(a.getScheduledEndAt()).isNull();
        assertThat(a.getRequestedDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(a.getPreferredTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(a.isFollowUp()).isFalse();
        // Placeholder duration keeps the NOT NULL / 5–240 CHECK satisfied before scheduling.
        assertThat(a.getDurationMinutes()).isBetween((short) 5, (short) 240);
    }

    @Test
    void preferred_time_is_optional_on_a_request() {
        Appointment a = Appointment.request(
                UuidV7.generate(), PATIENT, ACCOUNT, PHYSIO,
                LocalDate.of(2026, 6, 15), null, null, null);

        assertThat(a.getPreferredTime()).isNull();
        assertThat(a.getReason()).isNull();
        assertThat(a.getRequestedDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void schedule_assigns_the_time_and_confirms() {
        Appointment a = Appointment.request(
                UuidV7.generate(), PATIENT, ACCOUNT, PHYSIO,
                LocalDate.of(2026, 6, 15), null, "Lower back pain", null);
        Instant at = Instant.parse("2026-06-15T09:00:00Z");
        Instant now = Instant.parse("2026-06-10T08:00:00Z");

        a.schedule(at, (short) 45, now);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(a.getScheduledAt()).isEqualTo(at);
        assertThat(a.getScheduledEndAt()).isEqualTo(at.plus(45, ChronoUnit.MINUTES));
        assertThat(a.getDurationMinutes()).isEqualTo((short) 45);
        assertThat(a.getConfirmedAt()).isEqualTo(now);
    }

    @Test
    void scheduled_constructor_derives_requested_date_from_the_instant() {
        Instant at = Instant.parse("2026-06-15T03:30:00Z");
        Appointment a = new Appointment(
                UuidV7.generate(), PATIENT, ACCOUNT, PHYSIO, at, (short) 30, "reason", null);

        assertThat(a.getScheduledAt()).isEqualTo(at);
        assertThat(a.getScheduledEndAt()).isEqualTo(at.plus(30, ChronoUnit.MINUTES));
        assertThat(a.getRequestedDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(a.isFollowUp()).isFalse();
    }
}

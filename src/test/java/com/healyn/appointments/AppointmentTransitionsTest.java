package com.healyn.appointments;

import com.healyn.appointments.domain.AppointmentStatus;
import com.healyn.appointments.service.AppointmentTransitions;
import com.healyn.common.error.ConflictException;
import com.healyn.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentTransitionsTest {

    @Test
    void requested_allows_cancel_only_via_transitions() {
        // REQUESTED→CONFIRMED is rerouted to /schedule; RESCHEDULED goes through /reschedule.
        assertAllowed(AppointmentStatus.REQUESTED, EnumSet.of(
                AppointmentStatus.CANCELLED));
    }

    @Test
    void confirmed_allows_start_cancel_noshow_via_transitions() {
        // RESCHEDULED is excluded — it goes through /reschedule, not /transitions.
        assertAllowed(AppointmentStatus.CONFIRMED, EnumSet.of(
                AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.NO_SHOW));
    }

    @Test
    void in_progress_allows_complete_cancel_only() {
        assertAllowed(AppointmentStatus.IN_PROGRESS, EnumSet.of(
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED));
    }

    @Test
    void terminal_states_reject_everything() {
        for (AppointmentStatus terminal : EnumSet.of(
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.NO_SHOW,
                AppointmentStatus.RESCHEDULED)) {
            for (AppointmentStatus to : AppointmentStatus.values()) {
                assertThat(AppointmentTransitions.isAllowed(terminal, to))
                        .as("%s -> %s must be denied", terminal, to)
                        .isFalse();
            }
        }
    }

    @Test
    void requireAllowed_throws_conflict_for_invalid_transition() {
        assertThatThrownBy(() -> AppointmentTransitions.requireAllowed(
                AppointmentStatus.REQUESTED, AppointmentStatus.COMPLETED))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.APPOINTMENT_INVALID_TRANSITION);
    }

    private static void assertAllowed(AppointmentStatus from, Set<AppointmentStatus> allowed) {
        for (AppointmentStatus to : AppointmentStatus.values()) {
            boolean expected = allowed.contains(to);
            assertThat(AppointmentTransitions.isAllowed(from, to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(expected);
        }
    }
}

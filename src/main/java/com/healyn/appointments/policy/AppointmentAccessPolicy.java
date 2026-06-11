package com.healyn.appointments.policy;

import com.healyn.appointments.domain.Appointment;
import com.healyn.appointments.domain.AppointmentStatus;
import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.ForbiddenException;
import com.healyn.patients.policy.AccessMode;
import com.healyn.patients.policy.PatientAccessPolicy;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
public class AppointmentAccessPolicy {

    private static final Set<AppointmentStatus> PHYSIO_ONLY_TARGETS = EnumSet.of(
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_PROGRESS,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.NO_SHOW,
            // Only the physiotherapist declines a request (REQUESTED → REJECTED).
            AppointmentStatus.REJECTED);

    private final PatientAccessPolicy patientAccess;

    public AppointmentAccessPolicy(PatientAccessPolicy patientAccess) {
        this.patientAccess = patientAccess;
    }

    public void requireRead(UUID actorId, AccountRole role, Appointment appt) {
        patientAccess.requireAccess(actorId, role, appt.getPatientId(), AccessMode.READ);
    }

    public void requireBook(UUID actorId, AccountRole role, UUID patientId) {
        patientAccess.requireAccess(actorId, role, patientId, AccessMode.WRITE);
    }

    public void requireReschedule(UUID actorId, AccountRole role, Appointment appt) {
        patientAccess.requireAccess(actorId, role, appt.getPatientId(), AccessMode.WRITE);
    }

    /// Only the physiotherapist assigns the final time to a request (APPOINTMENT_FLOW §2).
    public void requireSchedule(AccountRole role) {
        if (role != AccountRole.ROLE_PHYSIO) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                    "Only the physiotherapist can schedule an appointment");
        }
    }

    /// Only the physiotherapist creates follow-ups (APPOINTMENT_FLOW §6a).
    public void requireCreateFollowUp(AccountRole role) {
        if (role != AccountRole.ROLE_PHYSIO) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                    "Only the physiotherapist can create a follow-up");
        }
    }

    public void requireTransition(UUID actorId, AccountRole role, Appointment appt, AppointmentStatus target) {
        if (PHYSIO_ONLY_TARGETS.contains(target)) {
            if (role != AccountRole.ROLE_PHYSIO) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN,
                        "Only the physiotherapist can transition to " + target);
            }
            return;
        }
        patientAccess.requireAccess(actorId, role, appt.getPatientId(), AccessMode.WRITE);
    }
}

package com.healyn.discussion.policy;

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
public class DiscussionAccessPolicy {

    private static final Set<AppointmentStatus> WRITE_BLOCKED_FOR_PATIENT_SIDE = EnumSet.of(
            AppointmentStatus.CANCELLED,
            AppointmentStatus.NO_SHOW);

    private final PatientAccessPolicy patientAccess;

    public DiscussionAccessPolicy(PatientAccessPolicy patientAccess) {
        this.patientAccess = patientAccess;
    }

    public void requireRead(UUID actorId, AccountRole role, Appointment appt) {
        if (role == AccountRole.ROLE_PHYSIO) return;
        patientAccess.requireAccess(actorId, role, appt.getPatientId(), AccessMode.READ);
    }

    public void requireWrite(UUID actorId, AccountRole role, Appointment appt) {
        if (role == AccountRole.ROLE_PHYSIO) return;
        patientAccess.requireAccess(actorId, role, appt.getPatientId(), AccessMode.WRITE);
        if (WRITE_BLOCKED_FOR_PATIENT_SIDE.contains(appt.getStatus())) {
            throw new ForbiddenException(ErrorCode.DISCUSSION_APPOINTMENT_TERMINAL,
                    "Discussion is read-only on " + appt.getStatus() + " appointments");
        }
    }
}

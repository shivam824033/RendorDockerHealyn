package com.healyn.treatmentnotes.policy;

import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.ForbiddenException;
import com.healyn.patients.policy.AccessMode;
import com.healyn.patients.policy.PatientAccessPolicy;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TreatmentNoteAccessPolicy {

    private final PatientAccessPolicy patientAccess;

    public TreatmentNoteAccessPolicy(PatientAccessPolicy patientAccess) {
        this.patientAccess = patientAccess;
    }

    /** Only the physiotherapist authors clinical notes. */
    public void requireWrite(AccountRole role) {
        if (role != AccountRole.ROLE_PHYSIO) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                    "Only the physiotherapist can write treatment notes");
        }
    }

    /** The physiotherapist, or a patient-side account with read access to the patient. */
    public void requireRead(UUID actorId, AccountRole role, UUID patientId) {
        if (role == AccountRole.ROLE_PHYSIO) return;
        patientAccess.requireAccess(actorId, role, patientId, AccessMode.READ);
    }

    /** Bulk note-existence status is a physiotherapist dashboard aid (which completed
     * appointments still need a note); patient-side accounts have no use for it. */
    public void requireStatusRead(AccountRole role) {
        if (role != AccountRole.ROLE_PHYSIO) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                    "Only the physiotherapist can read treatment-note status");
        }
    }
}

package com.healyn.files.policy;

import com.healyn.auth.domain.AccountRole;
import com.healyn.patients.policy.AccessMode;
import com.healyn.patients.policy.PatientAccessPolicy;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FileAccessPolicy {

    private final PatientAccessPolicy patientAccess;

    public FileAccessPolicy(PatientAccessPolicy patientAccess) {
        this.patientAccess = patientAccess;
    }

    public void requireWrite(UUID actorId, AccountRole role, UUID patientId) {
        patientAccess.requireAccess(actorId, role, patientId, AccessMode.WRITE);
    }

    public void requireRead(UUID actorId, AccountRole role, UUID patientId) {
        patientAccess.requireAccess(actorId, role, patientId, AccessMode.READ);
    }
}

package com.healyn.patients.policy;

import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.ForbiddenException;
import com.healyn.patients.domain.AccountPatient;
import com.healyn.patients.repository.AccountPatientRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PatientAccessPolicy {

    private final AccountPatientRepository links;

    public PatientAccessPolicy(AccountPatientRepository links) {
        this.links = links;
    }

    public void requireAccess(UUID accountId, AccountRole role, UUID patientId, AccessMode mode) {
        if (role == AccountRole.ROLE_PHYSIO) return;

        AccountPatient link = links.findLink(accountId, patientId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN, "No access to patient"));

        if (mode == AccessMode.WRITE && !link.isCanManage()) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Write access denied for patient");
        }
    }
}

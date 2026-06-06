package com.healyn.availability.policy;

import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class AvailabilityWriteAccess {

    public void requirePhysio(AccountRole role) {
        if (role != AccountRole.ROLE_PHYSIO) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Only the physiotherapist can manage availability");
        }
    }
}

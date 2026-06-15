package com.healyn.physio.policy;

import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.ForbiddenException;
import org.springframework.stereotype.Component;

/// Access rules for the physiotherapist profile. Editing (and avatar upload) is
/// limited to the single ROLE_PHYSIO account; reading is open to any authenticated
/// account, since every patient is shown the one physiotherapist's profile
/// (single-tenant — PROJECT_CONTEXT §5.2). Keeps authorization out of the
/// controller (CLAUDE.md hard rule #2).
@Component
public class PhysioProfilePolicy {

    public void requirePhysio(AccountRole role) {
        if (role != AccountRole.ROLE_PHYSIO) {
            throw new ForbiddenException(ErrorCode.PHYSIO_FORBIDDEN,
                    "Only the physiotherapist can edit the profile");
        }
    }
}

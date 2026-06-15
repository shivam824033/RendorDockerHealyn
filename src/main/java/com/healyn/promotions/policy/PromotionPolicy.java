package com.healyn.promotions.policy;

import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.ForbiddenException;
import org.springframework.stereotype.Component;

/// Access rules for clinic promotions. Reading is open to any authenticated account
/// (every patient is shown the one clinic's content); creating / updating / deleting /
/// reordering / activating is limited to the single ROLE_PHYSIO account. Keeps
/// authorization out of the controller (CLAUDE.md hard rule #2).
@Component
public class PromotionPolicy {

    public void requirePhysio(AccountRole role) {
        if (role != AccountRole.ROLE_PHYSIO) {
            throw new ForbiddenException(ErrorCode.PROMOTION_FORBIDDEN,
                    "Only the physiotherapist can manage clinic promotions");
        }
    }
}

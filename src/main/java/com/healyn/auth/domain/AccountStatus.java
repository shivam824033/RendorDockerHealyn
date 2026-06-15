package com.healyn.auth.domain;

public enum AccountStatus {
    ACTIVE,
    LOCKED,
    DISABLED,
    /// Deletion requested; inside the cancellable grace window. Still authenticates so the
    /// holder can cancel. Anonymization later flips this to {@link #DISABLED}.
    PENDING_DELETION
}

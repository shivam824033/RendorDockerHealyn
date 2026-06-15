package com.healyn.audit.domain;

public enum AuditAction {
    READ,
    CREATE,
    UPDATE,
    SOFT_DELETE,
    DOWNLOAD,
    EXPORT,
    ANONYMIZE,
    PURGE,
    CONSENT_GRANT,
    CONSENT_WITHDRAW
}

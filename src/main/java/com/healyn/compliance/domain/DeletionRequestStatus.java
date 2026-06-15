package com.healyn.compliance.domain;

/// Lifecycle of an account deletion request.
/// {@code REQUESTED} → cancellable grace window; {@code CANCELLED} → holder backed out;
/// {@code ANONYMIZED} → credentials/contact erased, identity PII redacted, clinical data
/// retained de-identified; {@code PURGED} → de-identified scaffolding removed after retention.
public enum DeletionRequestStatus {
    REQUESTED,
    CANCELLED,
    ANONYMIZED,
    PURGED
}

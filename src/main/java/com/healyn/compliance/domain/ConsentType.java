package com.healyn.compliance.domain;

/// The kinds of consent Healyn records. The first three are account-level (captured at
/// registration); {@code FAMILY_MEMBER_AUTHORITY} is per managed patient — the account
/// holder's attestation that they are authorised to manage that person's health data.
public enum ConsentType {
    TERMS_OF_SERVICE,
    PRIVACY_POLICY,
    HEALTH_DATA_PROCESSING,
    FAMILY_MEMBER_AUTHORITY
}

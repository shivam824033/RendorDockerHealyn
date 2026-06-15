package com.healyn.patients.port;

import java.util.UUID;

/// Seam letting the patients module record a Family-Member Authority consent — the account
/// holder's attestation that they are authorised to manage another person's health data —
/// without depending on the {@code compliance} module. The compliance module supplies the
/// implementation. Called inside the add-family-member transaction so a managed patient is
/// never created without its authority record.
public interface ConsentRecorderPort {

    void recordFamilyAuthority(UUID accountId, UUID patientId, String ipAddress, String userAgent);
}

package com.healyn.auth.port;

import java.util.UUID;

/// Seam letting registration record the account-level consents captured at signup (Terms,
/// Privacy Policy, Health-data processing) without {@code auth} depending on the
/// {@code compliance} module. The compliance module supplies the implementation; it records
/// each consent against the current published legal-document version. Called inside the
/// registration transaction so an account is never created without its consent trail.
public interface RegistrationConsentRecorder {

    void recordRegistrationConsents(UUID accountId, String ipAddress, String userAgent);
}

package com.healyn.auth.domain;

/// Why a {@link DeviceSession} was revoked. Persisted as text in
/// {@code device_sessions.revoke_reason}. Distinguishes a token that was
/// superseded by normal rotation ({@link #ROTATED}) — replaying it is theft —
/// from an administratively ended session ({@link #SIGNED_OUT} / {@link #ACCOUNT_REVOKE}),
/// which must reject quietly without punishing the account's other devices.
public enum RevokeReason {
    /// Superseded by refresh-token rotation. A later replay of this token is reuse.
    ROTATED,
    /// Ended from the "Signed-in devices" list (per-device sign out) or local logout.
    SIGNED_OUT,
    /// Revoked as part of an account-wide revoke (e.g. reuse fallout).
    ACCOUNT_REVOKE,
    /// Already inactive (expired) when presented again.
    EXPIRED
}

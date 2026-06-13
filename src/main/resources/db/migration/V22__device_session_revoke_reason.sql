-- Healyn V22: record why a device session was revoked.
--
-- A device session row is revoked for several reasons that are otherwise
-- indistinguishable (revoked_at alone): superseded by refresh-token rotation,
-- signed out from the devices list, or revoked account-wide. The reason lets the
-- refresh path tell an administratively signed-out session (just reject it) from
-- a replayed, already-rotated token (treat as theft). Nullable for legacy rows.
-- Reference: docs/SECURITY_GUIDELINES.md (session lifecycle).

ALTER TABLE device_sessions
    ADD COLUMN revoke_reason TEXT;

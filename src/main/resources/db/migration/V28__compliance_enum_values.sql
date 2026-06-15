-- Healyn V28: enum values for the compliance surface.
-- Reference: docs/SECURITY_GUIDELINES.md (Consent & Data Lifecycle), HEALYN_PRODUCTION_READINESS_AUDIT §5/§11 item 6.
-- Rules (V2): append new values; never reorder. ADD VALUE lives in its own migration,
-- separate from any table that uses the value, to avoid Postgres' "unsafe use of a new
-- enum value in the same transaction" error.

-- An account that has requested deletion and is inside its cancellable grace window.
-- It can still authenticate (so the holder can cancel); anonymization later flips it to DISABLED.
ALTER TYPE account_status ADD VALUE IF NOT EXISTS 'PENDING_DELETION';

-- Erasure + consent actions for the append-only audit trail (IDs only — Hard Rule #3).
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'ANONYMIZE';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'PURGE';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'CONSENT_GRANT';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'CONSENT_WITHDRAW';

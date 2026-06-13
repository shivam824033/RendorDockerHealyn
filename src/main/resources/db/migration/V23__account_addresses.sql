-- Healyn V23: account_addresses (one household postal address per account).
-- Reference: docs/DATABASE_SCHEMA.md §3.5a, docs/PATIENT_RELATIONSHIP_MODEL.md,
-- docs/API_STANDARDS.md §9.2.
--
-- The address belongs to the Account (the household login), not to an individual
-- Patient: it is captured once at registration and shared across the account's
-- primary patient and every family member. The physiotherapist resolves it per
-- patient (via account_patients) for communication and records.
--
-- account_id is the primary key, so the relation is 1:1 with accounts and a row
-- exists only when an address is set (legacy accounts created before this
-- migration simply have no row — the read layer tolerates its absence).
--
-- Account contact data, not clinical PHI in the audit sense (mirrors
-- notification_preferences): no soft-delete column, CASCADE with the account.
-- Additive only: nothing is dropped (CLAUDE.md hard rule #8).

CREATE TABLE IF NOT EXISTS account_addresses (
    account_id   UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    line1        VARCHAR(160) NOT NULL,
    line2        VARCHAR(160),
    city         VARCHAR(80)  NOT NULL,
    state        VARCHAR(80)  NOT NULL,
    postal_code  VARCHAR(16)  NOT NULL,
    country      VARCHAR(60)  NOT NULL DEFAULT 'India',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

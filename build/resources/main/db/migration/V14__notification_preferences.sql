-- Healyn V14: notification_preferences (per-account opt-out for push categories).
-- Reference: docs/DATABASE_SCHEMA.md §3.13b, docs/API_STANDARDS.md §9.8.
-- One row per account; every category defaults to opted-in (TRUE). A missing row means
-- "all enabled" — the GET endpoint synthesises defaults rather than persisting on read,
-- so a row only exists once the account has changed something. Categories map to
-- notification_kind values in NotificationCategory; the publisher skips enqueueing a row
-- for a recipient who has opted out of that category. Config, not PHI: no soft-delete.

CREATE TABLE notification_preferences (
    account_id              UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    appointment_updates     BOOLEAN NOT NULL DEFAULT TRUE,
    appointment_reminders   BOOLEAN NOT NULL DEFAULT TRUE,
    messages                BOOLEAN NOT NULL DEFAULT TRUE,
    treatment_notes         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

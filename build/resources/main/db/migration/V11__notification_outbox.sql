-- Healyn V11: notification_outbox (transactional outbox for outbound push).
-- Reference: docs/DATABASE_SCHEMA.md §3.13, docs/SYSTEM_ARCHITECTURE.md §4.3.
-- Enums (notification_kind / _status / _channel) created in V2.
-- One row per recipient; payload carries IDs only (no PHI) — CLAUDE.md Hard Rule #4.

CREATE TABLE notification_outbox (
    id                  UUID PRIMARY KEY,
    kind                notification_kind NOT NULL,
    channel             notification_channel NOT NULL DEFAULT 'FCM',
    target_account_id   UUID NOT NULL REFERENCES accounts(id),
    target_fcm_token    TEXT,
    payload             JSONB NOT NULL,
    status              notification_status NOT NULL DEFAULT 'PENDING',
    attempts            SMALLINT NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ,
    last_error          TEXT,
    correlation_id      UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notif_due
    ON notification_outbox(next_attempt_at)
    WHERE status = 'PENDING';

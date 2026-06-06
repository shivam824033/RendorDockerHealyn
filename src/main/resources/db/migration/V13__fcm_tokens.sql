-- Healyn V13: fcm_tokens (device push tokens for the outbox dispatcher).
-- Reference: docs/DATABASE_SCHEMA.md §3.x, docs/SYSTEM_ARCHITECTURE.md §3.1 (notifications owns FcmToken).
-- One row per device install. A token is globally unique while live; re-registering an
-- existing token reassigns it to the current account (device handed to another user / re-login).
-- The dispatcher resolves target_account_id -> live tokens at send time and prunes dead ones.

CREATE TABLE fcm_tokens (
    id            UUID PRIMARY KEY,
    account_id    UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token         TEXT NOT NULL,
    platform      VARCHAR(16) NOT NULL DEFAULT 'android',
    device_id     VARCHAR(128),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_fcm_tokens_token ON fcm_tokens(token) WHERE deleted_at IS NULL;
CREATE INDEX idx_fcm_tokens_account ON fcm_tokens(account_id) WHERE deleted_at IS NULL;

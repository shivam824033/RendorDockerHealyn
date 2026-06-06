-- Healyn V3: auth tables.
-- Reference: docs/DATABASE_SCHEMA.md §3.1–3.3.

CREATE TABLE accounts (
    id                  UUID         PRIMARY KEY,
    email               CITEXT       UNIQUE,
    phone_e164          TEXT         UNIQUE,
    password_hash       TEXT         NOT NULL,
    password_salt       BYTEA        NOT NULL,
    role                account_role NOT NULL,
    status              account_status NOT NULL DEFAULT 'ACTIVE',
    failed_login_count  INT          NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT accounts_email_or_phone CHECK (email IS NOT NULL OR phone_e164 IS NOT NULL),
    CONSTRAINT accounts_phone_e164_format CHECK (phone_e164 IS NULL OR phone_e164 ~ '^\+[1-9]\d{6,14}$')
);

CREATE INDEX idx_accounts_role_status ON accounts (role, status) WHERE deleted_at IS NULL;

CREATE TABLE device_sessions (
    id                   UUID         PRIMARY KEY,
    account_id           UUID         NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    refresh_token_hash   BYTEA        NOT NULL UNIQUE,
    device_id            TEXT         NOT NULL,
    device_label         TEXT,
    fcm_token            TEXT,
    ip_address           TEXT,
    user_agent           TEXT,
    issued_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ  NOT NULL,
    revoked_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_sessions_account_active
    ON device_sessions (account_id) WHERE revoked_at IS NULL;

CREATE TABLE otp_challenges (
    id            UUID         PRIMARY KEY,
    account_id    UUID         REFERENCES accounts(id) ON DELETE CASCADE,
    target        TEXT         NOT NULL,
    channel       otp_channel  NOT NULL,
    purpose       otp_purpose  NOT NULL,
    code_hash     BYTEA        NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    max_attempts  INT          NOT NULL DEFAULT 5,
    expires_at    TIMESTAMPTZ  NOT NULL,
    consumed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_challenges_target_active
    ON otp_challenges (target, purpose) WHERE consumed_at IS NULL;

-- Healyn V27: the physiotherapist's public-facing profile (personal + clinic +
-- social links + avatar). Reference: docs/SYSTEM_ARCHITECTURE.md §3 (physio
-- module), docs/PROJECT_CONTEXT.md §5.2 (single physiotherapist).
--
-- Single-tenant: exactly one ROLE_PHYSIO account, so this table holds one row,
-- keyed 1:1 by account_id (mirrors account_addresses). Patients read it to learn
-- who their physiotherapist is and how to reach the clinic; the physiotherapist
-- edits it. Not clinical PHI, so no soft-delete. All fields nullable — the
-- profile is filled in progressively. The avatar is stored in object storage
-- (S3/MinIO) under avatar_object_key, NOT in file_objects (that table is
-- patient-scoped); only the key + MIME are tracked here.
--
-- Additive only: no column or table is dropped (CLAUDE.md hard rule #8).

CREATE TABLE physio_profiles (
    account_id           UUID         PRIMARY KEY REFERENCES accounts(id),

    -- personal / professional
    display_name         VARCHAR(160),
    qualification        VARCHAR(160),
    experience_years     INT,
    specialization       VARCHAR(160),
    bio                  TEXT,

    -- clinic
    clinic_name          VARCHAR(160),
    clinic_address       TEXT,
    clinic_contact_phone VARCHAR(20),
    clinic_description    TEXT,

    -- social links
    instagram_url        VARCHAR(512),
    facebook_url         VARCHAR(512),
    linkedin_url         VARCHAR(512),
    website_url          VARCHAR(512),

    -- avatar (object-storage key + verified MIME); null until one is uploaded
    avatar_object_key    VARCHAR(512),
    avatar_mime          VARCHAR(64),

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT physio_profiles_experience_range
        CHECK (experience_years IS NULL OR (experience_years >= 0 AND experience_years <= 80))
);

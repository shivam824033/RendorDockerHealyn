-- Healyn V30: clinic promotions — first-party content the single physiotherapist
-- publishes to patients (service cards, promotional banners, clinic announcements,
-- health tips). Reference: docs/FEATURE_ROADMAP.md F1.23, docs/SYSTEM_ARCHITECTURE.md
-- §3 (promotions module), docs/API_STANDARDS.md §9.10.
--
-- Single-tenant in Phase 1 (PROJECT_CONTEXT §5.2): one ROLE_PHYSIO account authors
-- the content; every patient reads the active, in-window rows. Not clinical PHI, so
-- Hard Rule #7 (no hard-delete) does not apply — but we soft-delete anyway (deleted_at)
-- for an auditable trail and to keep cover-object cleanup explicit. The cover image is
-- stored in object storage under cover_object_key (NOT file_objects — that table is
-- patient-scoped), mirroring physio_profiles.avatar_object_key.
--
-- clinic_id is a Phase-3 multi-clinic enabler (FEATURE_ROADMAP F3.4): nullable, always
-- NULL in Phase 1, never exposed by the API. The patient query is unscoped.
--
-- Additive only: no column or table is dropped (CLAUDE.md hard rule #8).

CREATE TABLE promotions (
    id                 UUID         PRIMARY KEY,

    -- Phase-3 multi-clinic enabler; NULL and unexposed in Phase 1.
    clinic_id          UUID,
    -- The physiotherapist account that authored the content.
    created_by         UUID         NOT NULL REFERENCES accounts(id),

    title              VARCHAR(160) NOT NULL,
    short_description  VARCHAR(280),
    long_description   TEXT,
    service_category   VARCHAR(80),

    -- Call-to-action: a closed in-app action (NONE | BOOK_APPOINTMENT | CALL_CLINIC)
    -- plus the button label. No external/marketing URLs in Phase 1.
    cta_text           VARCHAR(40),
    cta_action         VARCHAR(32)  NOT NULL DEFAULT 'NONE',

    -- cover image (object-storage key + verified MIME); NULL until one is uploaded
    cover_object_key   VARCHAR(512),
    cover_mime         VARCHAR(64),

    -- ordering: lower display_order appears first on the patient surface
    display_order      INT          NOT NULL DEFAULT 0,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,

    -- optional scheduling window; NULL bound = open-ended on that side
    starts_at          TIMESTAMPTZ,
    ends_at            TIMESTAMPTZ,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT promotions_schedule_range
        CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT promotions_cta_action
        CHECK (cta_action IN ('NONE', 'BOOK_APPOINTMENT', 'CALL_CLINIC'))
);

-- Patient read path: active, not-deleted rows ordered by display_order then newest.
CREATE INDEX promotions_patient_idx
    ON promotions (display_order, created_at DESC)
    WHERE deleted_at IS NULL AND active;

-- FK index for the Phase-3 clinic scoping (always NULL today; kept for the enabler).
CREATE INDEX promotions_clinic_idx ON promotions (clinic_id);

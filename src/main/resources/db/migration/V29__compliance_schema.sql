-- Healyn V29: compliance surface — legal documents, consent records, account deletion requests.
-- Reference: docs/SECURITY_GUIDELINES.md (Consent & Data Lifecycle),
--            docs/SYSTEM_ARCHITECTURE.md §3.1 (compliance module),
--            HEALYN_PRODUCTION_READINESS_AUDIT §5/§11 item 6 (DPDP Act 2023 / HIPAA-aligned).
-- The brand-new enums below are CREATE TYPE (not ALTER ... ADD VALUE), so they are safe to
-- create and use within this one migration.

DO $$ BEGIN
    CREATE TYPE consent_type AS ENUM (
        'TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'HEALTH_DATA_PROCESSING', 'FAMILY_MEMBER_AUTHORITY'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE legal_document_kind AS ENUM ('PRIVACY_POLICY', 'TERMS_OF_SERVICE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE deletion_request_status AS ENUM ('REQUESTED', 'CANCELLED', 'ANONYMIZED', 'PURGED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Versioned legal documents (Privacy Policy / Terms). Consent rows bind to a specific
-- version so we can prove what text a person agreed to. Exactly one current row per
-- (kind, locale) is enforced by the partial unique index.
CREATE TABLE legal_documents (
    id              UUID                 PRIMARY KEY,
    kind            legal_document_kind  NOT NULL,
    version         TEXT                 NOT NULL,
    locale          TEXT                 NOT NULL DEFAULT 'en',
    title           TEXT                 NOT NULL,
    body_markdown   TEXT                 NOT NULL,
    effective_at    TIMESTAMPTZ          NOT NULL,
    is_current      BOOLEAN              NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ          NOT NULL DEFAULT now(),
    CONSTRAINT legal_documents_kind_version_locale UNIQUE (kind, version, locale)
);

CREATE UNIQUE INDEX idx_legal_documents_current
    ON legal_documents (kind, locale) WHERE is_current;

-- Demonstrable consent (DPDP Act 2023). Account-level consents (terms, privacy, health-data
-- processing) have a null patient_id; a FAMILY_MEMBER_AUTHORITY consent is keyed to the
-- managed patient. document_version snapshots the agreed legal-doc version at grant time.
CREATE TABLE consent_records (
    id                 UUID          PRIMARY KEY,
    account_id         UUID          NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    patient_id         UUID          REFERENCES patients(id) ON DELETE CASCADE,
    consent_type       consent_type  NOT NULL,
    legal_document_id  UUID          REFERENCES legal_documents(id),
    document_version   TEXT,
    granted            BOOLEAN       NOT NULL,
    granted_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    withdrawn_at       TIMESTAMPTZ,
    ip_address         TEXT,
    user_agent         TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_consent_records_account ON consent_records (account_id);
CREATE INDEX idx_consent_records_patient ON consent_records (patient_id) WHERE patient_id IS NOT NULL;
CREATE INDEX idx_consent_records_legal_document ON consent_records (legal_document_id)
    WHERE legal_document_id IS NOT NULL;

-- Right-to-erasure requests. A request enters a cancellable grace window (purge_after);
-- when it elapses the scheduled job anonymizes account credentials/contact and redacts
-- patient identity PII (clinical rows are retained, de-identified — Hard Rule #7).
-- The partial unique index allows only one active (REQUESTED) request per account.
CREATE TABLE account_deletion_requests (
    id              UUID                     PRIMARY KEY,
    account_id      UUID                     NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    status          deletion_request_status  NOT NULL DEFAULT 'REQUESTED',
    reason          TEXT,
    requested_at    TIMESTAMPTZ              NOT NULL DEFAULT now(),
    purge_after     TIMESTAMPTZ              NOT NULL,
    anonymized_at   TIMESTAMPTZ,
    purged_at       TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ              NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ              NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_account_deletion_active
    ON account_deletion_requests (account_id) WHERE status = 'REQUESTED';
CREATE INDEX idx_account_deletion_due
    ON account_deletion_requests (purge_after) WHERE status = 'REQUESTED';

-- Seed placeholder current documents so registration consent has a version to bind to.
-- DRAFT copy — must be replaced by legal-reviewed text before launch (audit §5).
INSERT INTO legal_documents (id, kind, version, locale, title, body_markdown, effective_at, is_current)
VALUES
    (gen_random_uuid(), 'PRIVACY_POLICY', '2026-06-14', 'en', 'Healyn Privacy Policy',
     '# Healyn Privacy Policy

**DRAFT — PENDING LEGAL REVIEW. Not for production use.**

This placeholder describes how Healyn processes Protected Health Information for the
purpose of delivering physiotherapy care. Replace this body with legal-reviewed text
covering: data collected, lawful basis (DPDP Act 2023 consent), retention, your rights
(access, correction, erasure), and contact details of the data fiduciary.',
     now(), true),
    (gen_random_uuid(), 'TERMS_OF_SERVICE', '2026-06-14', 'en', 'Healyn Terms of Service',
     '# Healyn Terms of Service

**DRAFT — PENDING LEGAL REVIEW. Not for production use.**

This placeholder sets out the terms governing use of Healyn. Replace this body with
legal-reviewed text covering: scope of service, account responsibilities, acceptable use,
liability, and the relationship between the account holder and managed family members.',
     now(), true);

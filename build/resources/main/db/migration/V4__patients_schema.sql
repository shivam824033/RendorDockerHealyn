-- Healyn V4: patients + account_patients.
-- Reference: docs/DATABASE_SCHEMA.md §3.4, docs/PATIENT_RELATIONSHIP_MODEL.md.

CREATE TABLE patients (
    id              UUID         PRIMARY KEY,
    full_name       VARCHAR(160) NOT NULL,
    date_of_birth   DATE         NOT NULL,
    sex             patient_sex  NOT NULL DEFAULT 'UNDISCLOSED',
    phone_e164      VARCHAR(20),
    email           CITEXT,
    blood_group     VARCHAR(3),
    allergies       TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT patients_phone_e164_format
        CHECK (phone_e164 IS NULL OR phone_e164 ~ '^\+[1-9]\d{6,14}$')
);

CREATE INDEX idx_patients_name_trgm
    ON patients USING gin (full_name gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE TABLE account_patients (
    account_id    UUID                 NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    patient_id    UUID                 NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    relationship  patient_relationship NOT NULL,
    is_primary    BOOLEAN              NOT NULL DEFAULT FALSE,
    can_manage    BOOLEAN              NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ          NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, patient_id)
);

CREATE UNIQUE INDEX idx_account_one_primary
    ON account_patients(account_id)
    WHERE is_primary = TRUE;

CREATE INDEX idx_account_patients_patient
    ON account_patients(patient_id);

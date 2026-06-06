-- Healyn V8: treatment notes (physiotherapist's clinical note per appointment).
-- Reference: docs/DATABASE_SCHEMA.md §3.9, docs/APPOINTMENT_FLOW.md §3.1.
-- One note per appointment (UNIQUE on appointment_id). patient_id is denormalized
-- for a fast patient-timeline read without joining through appointments.

CREATE TABLE treatment_notes (
    id                     UUID         PRIMARY KEY,
    appointment_id         UUID         NOT NULL UNIQUE REFERENCES appointments(id) ON DELETE RESTRICT,
    patient_id             UUID         NOT NULL REFERENCES patients(id),
    author_account_id      UUID         NOT NULL REFERENCES accounts(id),
    diagnosis              TEXT,
    notes                  TEXT,
    recovery_instructions  TEXT,
    next_review_at         TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    CONSTRAINT tnotes_field_lengths CHECK (
        (diagnosis IS NULL OR char_length(diagnosis) <= 8000)
        AND (notes IS NULL OR char_length(notes) <= 8000)
        AND (recovery_instructions IS NULL OR char_length(recovery_instructions) <= 8000)
    )
);

CREATE INDEX idx_tnotes_patient
    ON treatment_notes (patient_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_tnotes_patient_created
    ON treatment_notes (patient_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

-- Healyn V6: appointments.
-- Reference: docs/DATABASE_SCHEMA.md §3.8, docs/APPOINTMENT_FLOW.md §3, §4.

CREATE TABLE appointments (
    id                    UUID         PRIMARY KEY,
    patient_id            UUID         NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
    booked_by_account_id  UUID         NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    physiotherapist_id    UUID         NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    scheduled_at          TIMESTAMPTZ  NOT NULL,
    scheduled_end_at      TIMESTAMPTZ  NOT NULL,
    duration_minutes      SMALLINT     NOT NULL DEFAULT 30,
    status                appointment_status        NOT NULL DEFAULT 'REQUESTED',
    reason                VARCHAR(280),
    cancel_reason         appointment_cancel_reason,
    cancel_note           TEXT,
    rescheduled_from_id   UUID         REFERENCES appointments(id),
    confirmed_at          TIMESTAMPTZ,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,
    CONSTRAINT appointments_duration_range
        CHECK (duration_minutes BETWEEN 5 AND 240),
    CONSTRAINT appointments_end_after_start
        CHECK (scheduled_end_at > scheduled_at)
);

-- Plain column references are always IMMUTABLE; timestamptz arithmetic is STABLE
-- and therefore cannot appear directly in index/EXCLUDE expressions.
ALTER TABLE appointments
    ADD CONSTRAINT appointments_no_physio_overlap
    EXCLUDE USING gist (
        physiotherapist_id WITH =,
        tstzrange(scheduled_at, scheduled_end_at, '[)') WITH &&
    )
    WHERE (status IN ('CONFIRMED', 'IN_PROGRESS'));

CREATE INDEX idx_appt_physio_scheduled
    ON appointments (physiotherapist_id, scheduled_at)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_appt_patient_scheduled
    ON appointments (patient_id, scheduled_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_appt_status_scheduled
    ON appointments (status, scheduled_at)
    WHERE deleted_at IS NULL;

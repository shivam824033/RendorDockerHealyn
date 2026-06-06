-- Healyn V5: availability_rules + blackout_windows.
-- Reference: docs/DATABASE_SCHEMA.md §3.6–§3.7, docs/APPOINTMENT_FLOW.md §1, §4, §5.

CREATE TABLE availability_rules (
    id                  UUID         PRIMARY KEY,
    physiotherapist_id  UUID         NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    day_of_week         SMALLINT     NOT NULL,
    start_time          TIME         NOT NULL,
    end_time            TIME         NOT NULL,
    slot_minutes        SMALLINT     NOT NULL DEFAULT 30,
    timezone            VARCHAR(64)  NOT NULL,
    effective_from      DATE         NOT NULL,
    effective_to        DATE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT availability_rules_dow_range
        CHECK (day_of_week BETWEEN 0 AND 6),
    CONSTRAINT availability_rules_time_order
        CHECK (end_time > start_time),
    CONSTRAINT availability_rules_slot_minutes_range
        CHECK (slot_minutes BETWEEN 5 AND 240),
    CONSTRAINT availability_rules_slot_alignment
        CHECK (
            EXTRACT(EPOCH FROM start_time)::int % (slot_minutes * 60) = 0
            AND EXTRACT(EPOCH FROM end_time)::int   % (slot_minutes * 60) = 0
        ),
    CONSTRAINT availability_rules_effective_order
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_availability_physio_dow
    ON availability_rules (physiotherapist_id, day_of_week);

CREATE INDEX idx_availability_active
    ON availability_rules (physiotherapist_id)
    WHERE effective_to IS NULL;

CREATE TABLE blackout_windows (
    id                  UUID         PRIMARY KEY,
    physiotherapist_id  UUID         NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    starts_at           TIMESTAMPTZ  NOT NULL,
    ends_at             TIMESTAMPTZ  NOT NULL,
    reason              VARCHAR(200),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT blackout_windows_time_order
        CHECK (ends_at > starts_at),
    CONSTRAINT blackout_windows_no_overlap
        EXCLUDE USING gist (
            physiotherapist_id WITH =,
            tstzrange(starts_at, ends_at, '[)') WITH &&
        )
);

CREATE INDEX idx_blackout_windows_physio_range
    ON blackout_windows USING gist (
        physiotherapist_id,
        tstzrange(starts_at, ends_at, '[)')
    );

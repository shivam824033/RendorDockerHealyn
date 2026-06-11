-- Healyn V17: human-friendly Appointment Number (PHY-YYYYMMDD-NNNN).
-- Reference: docs/APPOINTMENT_FLOW.md, docs/DATABASE_SCHEMA.md §3.8,
-- docs/FEATURE_ROADMAP.md "Identifiers & lifecycle note".
--
-- A business identifier separate from the UUID primary key (never shown to users).
-- The YYYYMMDD stem is the row's creation date in the CLINIC timezone; NNNN is a per-day
-- counter. Generation is application-side (AppointmentNumberGenerator) so the stem uses the
-- same clinic zone as the rest of the app and Chunk 3 can derive child suffixes (-R1/-F1)
-- in Java. This migration provisions the counter table, the column, and backfills history.
--
-- Additive only: no column or table is dropped (CLAUDE.md hard rule #8).

-- Per-day counter. last_seq is the highest number issued for that clinic-local day.
CREATE TABLE appointment_daily_counters (
    day        DATE    PRIMARY KEY,
    last_seq   INTEGER NOT NULL
);

ALTER TABLE appointments ADD COLUMN IF NOT EXISTS appointment_number VARCHAR(32);

-- Backfill existing rows: per clinic-local creation day, numbered in creation order.
-- The clinic zone literal here must match the runtime default (healyn.clinic.timezone =
-- Asia/Kolkata). If an operator overrides the zone, only these legacy stems may differ by
-- a day at the boundary — new rows always use the configured zone.
WITH ordered AS (
    SELECT id,
           (created_at AT TIME ZONE 'Asia/Kolkata')::date AS d,
           row_number() OVER (
               PARTITION BY (created_at AT TIME ZONE 'Asia/Kolkata')::date
               ORDER BY created_at, id) AS rn
      FROM appointments
)
UPDATE appointments a
   SET appointment_number = 'PHY-' || to_char(o.d, 'YYYYMMDD') || '-' || lpad(o.rn::text, 4, '0')
  FROM ordered o
 WHERE a.id = o.id;

-- Seed the counters so new same-day inserts continue the sequence rather than restarting.
INSERT INTO appointment_daily_counters (day, last_seq)
SELECT (created_at AT TIME ZONE 'Asia/Kolkata')::date, count(*)
  FROM appointments
 GROUP BY 1;

ALTER TABLE appointments ALTER COLUMN appointment_number SET NOT NULL;

ALTER TABLE appointments
    ADD CONSTRAINT appointments_appointment_number_key UNIQUE (appointment_number);

-- Healyn V15: request-first appointments.
-- Reference: docs/APPOINTMENT_FLOW.md §1, §2, §3, §6, §6a; docs/DATABASE_SCHEMA.md §3.8.
--
-- Shifts booking from "patient picks the exact slot" to "patient requests a date,
-- the physiotherapist finalises the date & time". A REQUESTED appointment may now
-- carry no scheduled_at until the physiotherapist schedules it.
--   requested_date - the date the patient asked for (mandatory at request time)
--   preferred_time - an optional, non-binding time-of-day hint from the patient
--   is_follow_up   - true when the physiotherapist created this as a follow-up
--
-- Additive only: no column or table is dropped (CLAUDE.md hard rule #8).

-- 1. The time is no longer present at request time; the physiotherapist sets it later.
--    DROP NOT NULL is a no-op if already nullable, so this is safe to re-run.
ALTER TABLE appointments ALTER COLUMN scheduled_at DROP NOT NULL;
ALTER TABLE appointments ALTER COLUMN scheduled_end_at DROP NOT NULL;

-- 2. New request-first columns.
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS requested_date DATE;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS preferred_time TIME;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS is_follow_up BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Backfill requested_date for existing rows from the scheduled instant (stored UTC).
--    Pre-V15 rows are all scheduled, so requested_date is always derivable here.
UPDATE appointments
   SET requested_date = (scheduled_at AT TIME ZONE 'UTC')::date
 WHERE requested_date IS NULL
   AND scheduled_at IS NOT NULL;

ALTER TABLE appointments ALTER COLUMN requested_date SET NOT NULL;

-- 4. A confirmed/active appointment must carry a concrete time; an unscheduled
--    REQUESTED (or a cancelled/rescheduled request) may not. This also keeps the
--    physio-overlap EXCLUDE index safe: only rows with a real time enter its WHERE set.
--    (appointments_end_after_start from V6 already tolerates NULLs — NULL comparison
--    yields UNKNOWN, which a CHECK treats as satisfied — so it needs no change.)
DO $$ BEGIN
    ALTER TABLE appointments
        ADD CONSTRAINT appointments_scheduled_when_active CHECK (
            status NOT IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW')
            OR scheduled_at IS NOT NULL
        );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 5. The physiotherapist's pending request queue, ordered by the date asked for.
CREATE INDEX IF NOT EXISTS idx_appt_requested_date
    ON appointments (physiotherapist_id, requested_date)
    WHERE status = 'REQUESTED' AND deleted_at IS NULL;

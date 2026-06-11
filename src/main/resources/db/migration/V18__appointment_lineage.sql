-- Healyn V18: appointment parent-child lineage.
-- Reference: docs/APPOINTMENT_FLOW.md §6, §6a; docs/DATABASE_SCHEMA.md §2, §3.8,
-- docs/FEATURE_ROADMAP.md "Identifiers & lifecycle note".
--
-- Models the lineage that links appointments which spawn a *new bookable row* (a reschedule
-- or a follow-up): the row that replaces or follows another. In-place lifecycle changes
-- (confirm/start/complete/cancel) are NOT modelled here — they belong to the appointment_events
-- timeline (a later chunk). Three additive columns:
--   root_appointment_id   - the origin of the lineage; a root is its own root (= id).
--   source_appointment_id - the immediate appointment this row derived from (null on a root).
--   child_kind            - how it derived (RESCHEDULE / FOLLOW_UP / REVIEW / REOPEN); null on a root.
--
-- The existing rescheduled_from_id is retained for backward compatibility; for a reschedule it
-- equals source_appointment_id where child_kind = 'RESCHEDULE'. Numbering: a child reuses its
-- root's number stem plus a per-kind suffix (-R1, -F1, ...), derived application-side.
--
-- Additive only: no column or table is dropped (CLAUDE.md hard rule #8).

DO $$ BEGIN
    CREATE TYPE appointment_child_kind AS ENUM ('RESCHEDULE', 'FOLLOW_UP', 'REVIEW', 'REOPEN');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

ALTER TABLE appointments ADD COLUMN IF NOT EXISTS root_appointment_id   UUID;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS source_appointment_id UUID;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS child_kind            appointment_child_kind;

-- Self-referential FKs. No ON DELETE clause: clinical data is soft-deleted only (rule #7),
-- so a referenced appointment is never physically removed.
DO $$ BEGIN
    ALTER TABLE appointments
        ADD CONSTRAINT appointments_root_fk
        FOREIGN KEY (root_appointment_id) REFERENCES appointments (id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    ALTER TABLE appointments
        ADD CONSTRAINT appointments_source_fk
        FOREIGN KEY (source_appointment_id) REFERENCES appointments (id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Backfill the immediate source from the legacy reschedule pointer, and mark those rows as
-- reschedule children. Pre-V18 follow-ups carried no source link, so they stay roots.
UPDATE appointments
   SET source_appointment_id = rescheduled_from_id
 WHERE rescheduled_from_id IS NOT NULL
   AND source_appointment_id IS NULL;

UPDATE appointments
   SET child_kind = 'RESCHEDULE'
 WHERE rescheduled_from_id IS NOT NULL
   AND child_kind IS NULL;

-- Backfill the lineage root by walking the source chain to its origin. Every chain terminates
-- at a row with no source (the original booking), so every row resolves to a root. There are no
-- cycles: a reschedule source is always an older row.
WITH RECURSIVE chain AS (
    SELECT id, source_appointment_id, id AS root_id
      FROM appointments
     WHERE source_appointment_id IS NULL
    UNION ALL
    SELECT a.id, a.source_appointment_id, c.root_id
      FROM appointments a
      JOIN chain c ON a.source_appointment_id = c.id
)
UPDATE appointments a
   SET root_appointment_id = chain.root_id
  FROM chain
 WHERE a.id = chain.id
   AND a.root_appointment_id IS NULL;

-- Every row now has a root (a row with no source is its own root).
ALTER TABLE appointments ALTER COLUMN root_appointment_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_appointments_root
    ON appointments (root_appointment_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_appointments_source
    ON appointments (source_appointment_id)
    WHERE deleted_at IS NULL;

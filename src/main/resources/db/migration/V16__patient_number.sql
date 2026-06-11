-- Healyn V16: human-friendly Patient ID (PAT-NNNNNN).
-- Reference: docs/PATIENT_RELATIONSHIP_MODEL.md, docs/DATABASE_SCHEMA.md §3.4,
-- docs/FEATURE_ROADMAP.md "Identifiers & lifecycle note".
--
-- A business identifier separate from the UUID primary key. The UUID stays the
-- technical PK and is NEVER shown to users; patient_number is what patients quote
-- and the physiotherapist references. It is assigned once at insert from a global
-- sequence and never changes (the column is insertable/updatable=false in JPA).
--
-- Additive only: no column or table is dropped (CLAUDE.md hard rule #8).

-- A fresh sequence's first nextval() returns its START value, so the first
-- patient on an empty database is PAT-100001.
CREATE SEQUENCE IF NOT EXISTS patient_number_seq START WITH 100001;

ALTER TABLE patients ADD COLUMN IF NOT EXISTS patient_number VARCHAR(20);

-- Backfill existing rows deterministically in creation order (PAT-100001, PAT-100002, …).
WITH ordered AS (
    SELECT id, row_number() OVER (ORDER BY created_at, id) AS rn
      FROM patients
     WHERE patient_number IS NULL
)
UPDATE patients p
   SET patient_number = 'PAT-' || (100000 + o.rn)
  FROM ordered o
 WHERE p.id = o.id;

-- Move the sequence past the backfilled block so new inserts never collide. On an
-- empty database there is nothing to skip, so the sequence keeps START 100001.
DO $$
DECLARE n bigint;
BEGIN
    SELECT count(*) INTO n FROM patients WHERE patient_number IS NOT NULL;
    IF n > 0 THEN
        PERFORM setval('patient_number_seq', 100000 + n, true);
    END IF;
END $$;

-- New rows take their number straight from the sequence — atomic and race-free,
-- with no application round-trip. Hibernate reads it back via @Generated(INSERT).
ALTER TABLE patients
    ALTER COLUMN patient_number SET DEFAULT 'PAT-' || nextval('patient_number_seq');

ALTER TABLE patients ALTER COLUMN patient_number SET NOT NULL;

ALTER TABLE patients
    ADD CONSTRAINT patients_patient_number_key UNIQUE (patient_number);

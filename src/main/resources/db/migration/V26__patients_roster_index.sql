-- Healyn V26: keyset index for the physiotherapist's patient roster.
-- Reference: docs/PATIENT_RELATIONSHIP_MODEL.md, docs/DATABASE_SCHEMA.md.
--
-- The physio roster is paginated newest-first (created_at DESC, id DESC) via the
-- cursor keyset pattern. A composite btree on (created_at, id) serves that order
-- for the default (unsearched) page; PostgreSQL walks it backwards for DESC.
-- Name search keeps using idx_patients_name_trgm (V4) and Patient ID prefix
-- search uses idx_patients_number_pattern (V21).
--
-- Additive only: no column or table is dropped (CLAUDE.md hard rule #8).

CREATE INDEX IF NOT EXISTS idx_patients_created_id
    ON patients (created_at, id)
    WHERE deleted_at IS NULL;

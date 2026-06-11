-- Healyn V21: prefix-scan indexes for global appointment search.
-- Reference: docs/API_STANDARDS.md §9.4 (GET /appointments/search),
-- docs/FEATURE_ROADMAP.md F1.22 (global search, P2), docs/DATABASE_SCHEMA.md §3.8.
--
-- The header autocomplete matches a typed prefix against the human-friendly
-- identifiers (PHY-… appointment numbers, PAT-… patient numbers). The existing
-- UNIQUE btree on each column is collation-aware, so it cannot serve a
-- `LIKE 'PHY-2026%'` prefix scan unless the database is in the C locale. These
-- text_pattern_ops indexes make the prefix scans index-backed regardless of the
-- server collation. Patient-name matching keeps using the V4 gin_trgm_ops index
-- (idx_patients_name_trgm) via ILIKE '%term%'.
--
-- Additive only (hard rule #8): two new partial indexes, nothing dropped.
-- Identifiers are stored upper-case, so the application upper-cases the typed
-- term and issues a case-sensitive LIKE against these indexes.

CREATE INDEX IF NOT EXISTS idx_appointments_number_pattern
    ON appointments (appointment_number text_pattern_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_patients_number_pattern
    ON patients (patient_number text_pattern_ops)
    WHERE deleted_at IS NULL;

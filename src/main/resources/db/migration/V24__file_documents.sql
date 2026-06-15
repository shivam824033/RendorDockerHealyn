-- Healyn V24: standalone per-patient document library on top of file_objects.
-- Reference: docs/FILE_STORAGE_GUIDELINES.md §3, docs/FEATURE_ROADMAP.md F1.15,
-- docs/DATABASE_SCHEMA.md §3.12.
--
-- Extends the appointment-scoped files pipeline so a file can be uploaded for a
-- patient WITHOUT an appointment (a "document library" entry) and listed per
-- patient, split by who uploaded it. All additive: no column or table is dropped.

-- LIBRARY = a directly-uploaded document; DISCUSSION = a discussion-message
-- attachment. The library listing filters to LIBRARY so chat attachments never
-- leak into it. (Mirrors the file_kind / file_status NAMED_ENUM pattern in V2.)
DO $$ BEGIN
    CREATE TYPE file_context AS ENUM ('DISCUSSION', 'LIBRARY');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

ALTER TABLE file_objects
    ADD COLUMN appointment_id  UUID REFERENCES appointments(id),
    ADD COLUMN uploaded_by_role account_role,
    ADD COLUMN upload_context   file_context,
    ADD COLUMN upload_source    VARCHAR(16),
    ADD CONSTRAINT file_upload_source_whitelist
        CHECK (upload_source IS NULL
               OR upload_source IN ('CAMERA', 'GALLERY', 'FILE', 'CONVERTED_PDF'));

-- Backfill: derive the uploader's role from the owning account.
UPDATE file_objects f
SET uploaded_by_role = a.role
FROM accounts a
WHERE a.id = f.owner_account_id;

-- Backfill: every existing file predates the library and is reachable only as a
-- discussion attachment, so files referenced by a message are DISCUSSION; any
-- orphan (none today) defaults to LIBRARY.
UPDATE file_objects
SET upload_context = 'DISCUSSION'
WHERE id IN (SELECT file_id FROM discussion_message_attachments);

UPDATE file_objects
SET upload_context = 'LIBRARY'
WHERE upload_context IS NULL;

ALTER TABLE file_objects
    ALTER COLUMN uploaded_by_role SET NOT NULL,
    ALTER COLUMN upload_context   SET NOT NULL,
    ALTER COLUMN upload_context   SET DEFAULT 'LIBRARY';

CREATE INDEX idx_file_appointment ON file_objects (appointment_id)
    WHERE appointment_id IS NOT NULL AND deleted_at IS NULL;

-- Drives the per-patient, per-uploader, newest-first cursor listing.
CREATE INDEX idx_file_library ON file_objects (patient_id, uploaded_by_role, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND upload_context = 'LIBRARY';

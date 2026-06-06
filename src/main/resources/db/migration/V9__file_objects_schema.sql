-- Healyn V9: file_objects (PHI file metadata; bytes live in S3-compatible storage).
-- Reference: docs/DATABASE_SCHEMA.md §3.12, docs/FILE_STORAGE_GUIDELINES.md.
--
-- mime_type note: the file_mime enum (V2) carries '/'-bearing labels
-- ('application/pdf', ...) which cannot map to a Hibernate @JdbcTypeCode(NAMED_ENUM)
-- Java enum the way every other enum in this codebase does. To avoid a one-off
-- global JDBC `stringtype=unspecified` setting, the column is VARCHAR constrained
-- to exactly the file_mime value set; file_mime remains the documented allowed set.

CREATE TABLE file_objects (
    id                  UUID         PRIMARY KEY,
    owner_account_id    UUID         NOT NULL REFERENCES accounts(id),
    patient_id          UUID         NOT NULL REFERENCES patients(id),
    kind                file_kind    NOT NULL,
    mime_type           VARCHAR(64)  NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    storage_key         VARCHAR(512) NOT NULL UNIQUE,
    size_bytes          BIGINT       NOT NULL,
    sha256_hex          CHAR(64),
    status              file_status  NOT NULL DEFAULT 'PENDING_UPLOAD',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    available_at        TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT file_size_range CHECK (size_bytes > 0 AND size_bytes <= 20 * 1024 * 1024),
    CONSTRAINT file_mime_whitelist
        CHECK (mime_type IN ('application/pdf', 'image/jpeg', 'image/png'))
);

CREATE INDEX idx_file_patient ON file_objects (patient_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_file_status ON file_objects (status) WHERE status = 'PENDING_UPLOAD';
CREATE INDEX idx_file_owner_created ON file_objects (owner_account_id, created_at);

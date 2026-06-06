-- Healyn V7: discussion (appointment-scoped messaging).
-- Reference: docs/DATABASE_SCHEMA.md §3.10, docs/DISCUSSION_SYSTEM_DESIGN.md.
-- Note: discussion_message_attachments is deferred to the files PR (depends on file_objects).

CREATE TABLE discussion_messages (
    id                  UUID                    PRIMARY KEY,
    appointment_id      UUID                    NOT NULL REFERENCES appointments(id) ON DELETE RESTRICT,
    sender_account_id   UUID                    NOT NULL REFERENCES accounts(id),
    sender_role         discussion_sender_role  NOT NULL,
    message_type        discussion_message_type NOT NULL,
    body                TEXT,
    created_at          TIMESTAMPTZ             NOT NULL DEFAULT now(),
    edited_at           TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT dmsg_body_or_attachment
        CHECK (body IS NOT NULL OR message_type = 'ATTACHMENT_ONLY'),
    CONSTRAINT dmsg_body_length
        CHECK (body IS NULL OR char_length(body) <= 2000)
);

CREATE INDEX idx_dmsg_appt_created
    ON discussion_messages (appointment_id, created_at)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_dmsg_appt_id_desc
    ON discussion_messages (appointment_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE discussion_read_markers (
    appointment_id        UUID         NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    account_id            UUID         NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    last_read_message_id  UUID         NOT NULL REFERENCES discussion_messages(id),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (appointment_id, account_id)
);

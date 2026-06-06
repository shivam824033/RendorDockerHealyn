-- Healyn V10: discussion_message_attachments (wires file_objects into discussion).
-- Reference: docs/DATABASE_SCHEMA.md §3.11, docs/DISCUSSION_SYSTEM_DESIGN.md §6.
-- Deferred from V7 because it depends on file_objects (V9).

CREATE TABLE discussion_message_attachments (
    message_id  UUID NOT NULL REFERENCES discussion_messages(id) ON DELETE CASCADE,
    file_id     UUID NOT NULL REFERENCES file_objects(id) ON DELETE RESTRICT,
    PRIMARY KEY (message_id, file_id)
);

CREATE INDEX idx_dmsg_att_file ON discussion_message_attachments(file_id);

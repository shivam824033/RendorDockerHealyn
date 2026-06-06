package com.healyn.discussion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "discussion_message_attachments")
@IdClass(DiscussionMessageAttachmentId.class)
public class DiscussionMessageAttachment {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Id
    @Column(name = "file_id", nullable = false, updatable = false)
    private UUID fileId;

    protected DiscussionMessageAttachment() {}

    public DiscussionMessageAttachment(UUID messageId, UUID fileId) {
        this.messageId = messageId;
        this.fileId = fileId;
    }

    public UUID getMessageId() { return messageId; }
    public UUID getFileId() { return fileId; }
}

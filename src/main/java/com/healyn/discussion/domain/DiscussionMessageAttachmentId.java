package com.healyn.discussion.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class DiscussionMessageAttachmentId implements Serializable {

    private UUID messageId;
    private UUID fileId;

    protected DiscussionMessageAttachmentId() {}

    public DiscussionMessageAttachmentId(UUID messageId, UUID fileId) {
        this.messageId = messageId;
        this.fileId = fileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscussionMessageAttachmentId other)) return false;
        return Objects.equals(messageId, other.messageId) && Objects.equals(fileId, other.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, fileId);
    }
}

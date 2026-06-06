package com.healyn.discussion.web;

import com.healyn.discussion.domain.DiscussionMessageType;
import com.healyn.discussion.domain.DiscussionSenderRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DiscussionDtos {

    private DiscussionDtos() {}

    public record PostMessageBody(DiscussionMessageType messageType, String body, List<UUID> fileIds) {}

    public record EditMessageBody(String body) {}

    public record MarkReadBody(UUID messageId) {}

    public record AttachmentView(
            UUID fileId,
            String kind,
            String mimeType,
            String originalFilename,
            long sizeBytes) {}

    public record MessageView(
            UUID id,
            UUID appointmentId,
            UUID senderAccountId,
            DiscussionSenderRole senderRole,
            DiscussionMessageType messageType,
            String body,
            List<AttachmentView> attachments,
            Instant createdAt,
            Instant editedAt) {}

    public record MessagePage(List<MessageView> items, String nextCursor) {}

    public record UnreadCountView(long unreadCount) {}
}

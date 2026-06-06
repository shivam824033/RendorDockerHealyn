package com.healyn.discussion.web;

import com.healyn.discussion.domain.DiscussionMessage;
import com.healyn.files.domain.FileObject;

import java.util.List;

public final class DiscussionMapper {

    private DiscussionMapper() {}

    public static DiscussionDtos.MessageView toView(DiscussionMessage m, List<FileObject> attachments) {
        List<DiscussionDtos.AttachmentView> views = attachments.stream()
                .map(DiscussionMapper::toAttachmentView)
                .toList();
        return new DiscussionDtos.MessageView(
                m.getId(),
                m.getAppointmentId(),
                m.getSenderAccountId(),
                m.getSenderRole(),
                m.getMessageType(),
                m.getBody(),
                views,
                m.getCreatedAt(),
                m.getEditedAt());
    }

    private static DiscussionDtos.AttachmentView toAttachmentView(FileObject f) {
        return new DiscussionDtos.AttachmentView(
                f.getId(),
                f.getKind().name(),
                f.getMimeType(),
                f.getOriginalFilename(),
                f.getSizeBytes());
    }
}

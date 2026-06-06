package com.healyn.discussion.adapter;

import com.healyn.discussion.repository.DiscussionMessageAttachmentRepository;
import com.healyn.files.port.FileReferenceGuard;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DiscussionFileReferenceGuard implements FileReferenceGuard {

    private final DiscussionMessageAttachmentRepository attachments;

    public DiscussionFileReferenceGuard(DiscussionMessageAttachmentRepository attachments) {
        this.attachments = attachments;
    }

    @Override
    public boolean isReferenced(UUID fileId) {
        return attachments.countByFileId(fileId) > 0;
    }
}

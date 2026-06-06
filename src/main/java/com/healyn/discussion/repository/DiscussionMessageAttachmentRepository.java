package com.healyn.discussion.repository;

import com.healyn.discussion.domain.DiscussionMessageAttachment;
import com.healyn.discussion.domain.DiscussionMessageAttachmentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DiscussionMessageAttachmentRepository
        extends JpaRepository<DiscussionMessageAttachment, DiscussionMessageAttachmentId> {

    List<DiscussionMessageAttachment> findByMessageId(UUID messageId);

    List<DiscussionMessageAttachment> findByMessageIdIn(Collection<UUID> messageIds);

    long countByFileId(UUID fileId);
}

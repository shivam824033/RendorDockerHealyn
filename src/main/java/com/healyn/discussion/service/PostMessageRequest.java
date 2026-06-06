package com.healyn.discussion.service;

import com.healyn.discussion.domain.DiscussionMessageType;

import java.util.List;
import java.util.UUID;

public record PostMessageRequest(DiscussionMessageType messageType, String body, List<UUID> fileIds) {

    public PostMessageRequest {
        fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
    }
}

package com.healyn.discussion.web;

import com.healyn.auth.domain.AccountRole;
import com.healyn.common.pagination.CursorPage;
import com.healyn.discussion.domain.DiscussionMessage;
import com.healyn.discussion.service.DiscussionService;
import com.healyn.discussion.service.EditMessageRequest;
import com.healyn.discussion.service.PostMessageRequest;
import com.healyn.files.domain.FileObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/appointments/{appointmentId}/messages")
public class DiscussionController {

    private final DiscussionService service;

    public DiscussionController(DiscussionService service) {
        this.service = service;
    }

    @GetMapping
    public DiscussionDtos.MessagePage list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("appointmentId") UUID appointmentId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        CursorPage<DiscussionMessage> page = service.list(actorId, role, appointmentId, cursor, limit);
        Map<UUID, List<FileObject>> attachments = service.attachmentsFor(
                page.items().stream().map(DiscussionMessage::getId).toList());
        List<DiscussionDtos.MessageView> views = page.items().stream()
                .map(m -> DiscussionMapper.toView(m, attachments.getOrDefault(m.getId(), List.of())))
                .toList();
        return new DiscussionDtos.MessagePage(views, page.nextCursor());
    }

    @PostMapping
    public ResponseEntity<DiscussionDtos.MessageView> post(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("appointmentId") UUID appointmentId,
            @RequestBody DiscussionDtos.PostMessageBody body) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        DiscussionMessage saved = service.post(actorId, role, appointmentId,
                new PostMessageRequest(body.messageType(), body.body(), body.fileIds()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DiscussionMapper.toView(saved, attachmentsOf(saved)));
    }

    @PatchMapping("/{messageId}")
    public DiscussionDtos.MessageView edit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("appointmentId") UUID appointmentId,
            @PathVariable("messageId") UUID messageId,
            @RequestBody DiscussionDtos.EditMessageBody body) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        DiscussionMessage edited = service.edit(actorId, role, appointmentId, messageId,
                new EditMessageRequest(body.body()));
        return DiscussionMapper.toView(edited, attachmentsOf(edited));
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("appointmentId") UUID appointmentId,
            @PathVariable("messageId") UUID messageId) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        service.delete(actorId, role, appointmentId, messageId);
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("appointmentId") UUID appointmentId,
            @RequestBody DiscussionDtos.MarkReadBody body) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        service.markRead(actorId, role, appointmentId, body.messageId());
    }

    @GetMapping("/unread-count")
    public DiscussionDtos.UnreadCountView unreadCount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("appointmentId") UUID appointmentId) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        return new DiscussionDtos.UnreadCountView(service.unreadCount(actorId, role, appointmentId));
    }

    private List<FileObject> attachmentsOf(DiscussionMessage msg) {
        return service.attachmentsFor(List.of(msg.getId())).getOrDefault(msg.getId(), List.of());
    }

    private static AccountRole roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? AccountRole.ROLE_ACCOUNT : AccountRole.valueOf(role);
    }
}

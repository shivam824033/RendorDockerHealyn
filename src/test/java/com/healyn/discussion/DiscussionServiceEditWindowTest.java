package com.healyn.discussion;

import com.healyn.appointments.repository.AppointmentRepository;
import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ConflictException;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.ForbiddenException;
import com.healyn.discussion.domain.DiscussionMessage;
import com.healyn.discussion.domain.DiscussionMessageType;
import com.healyn.discussion.domain.DiscussionSenderRole;
import com.healyn.discussion.policy.DiscussionAccessPolicy;
import com.healyn.discussion.repository.DiscussionMessageAttachmentRepository;
import com.healyn.discussion.repository.DiscussionMessageRepository;
import com.healyn.discussion.repository.DiscussionReadMarkerRepository;
import com.healyn.discussion.service.DiscussionService;
import com.healyn.files.repository.FileObjectRepository;
import com.healyn.discussion.service.EditMessageRequest;
import com.healyn.audit.service.AuditLogger;
import com.healyn.notifications.service.NotificationPublisher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscussionServiceEditWindowTest {

    private final DiscussionMessageRepository messages = mock(DiscussionMessageRepository.class);
    private final DiscussionMessageAttachmentRepository attachments = mock(DiscussionMessageAttachmentRepository.class);
    private final DiscussionReadMarkerRepository readMarkers = mock(DiscussionReadMarkerRepository.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final FileObjectRepository files = mock(FileObjectRepository.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final DiscussionAccessPolicy access = mock(DiscussionAccessPolicy.class);

    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID APPT = UUID.randomUUID();
    private static final UUID MSG = UUID.randomUUID();
    private static final Instant CREATED = Instant.parse("2026-06-01T10:00:00Z");

    @Test
    void edit_within_window_updates_body_and_sets_edited_at() {
        DiscussionMessage msg = newMessage(CREATED);
        when(messages.findByIdAndDeletedAtIsNull(MSG)).thenReturn(Optional.of(msg));
        DiscussionService svc = newService(CREATED.plus(Duration.ofMinutes(3)));

        DiscussionMessage out = svc.edit(SENDER, AccountRole.ROLE_ACCOUNT, APPT, MSG,
                new EditMessageRequest("edited body"));

        assertThat(out.getBody()).isEqualTo("edited body");
        assertThat(out.getEditedAt()).isNotNull();
    }

    @Test
    void edit_after_5_minutes_is_rejected() {
        DiscussionMessage msg = newMessage(CREATED);
        when(messages.findByIdAndDeletedAtIsNull(MSG)).thenReturn(Optional.of(msg));
        DiscussionService svc = newService(CREATED.plus(Duration.ofMinutes(5)).plus(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> svc.edit(SENDER, AccountRole.ROLE_ACCOUNT, APPT, MSG,
                new EditMessageRequest("too late")))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.DISCUSSION_EDIT_WINDOW_EXPIRED);
    }

    @Test
    void edit_by_non_sender_is_rejected() {
        DiscussionMessage msg = newMessage(CREATED);
        when(messages.findByIdAndDeletedAtIsNull(MSG)).thenReturn(Optional.of(msg));
        DiscussionService svc = newService(CREATED.plus(Duration.ofMinutes(1)));

        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> svc.edit(other, AccountRole.ROLE_ACCOUNT, APPT, MSG,
                new EditMessageRequest("not mine")))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.DISCUSSION_NOT_SENDER);
    }

    @Test
    void delete_within_window_sets_deleted_at() {
        DiscussionMessage msg = newMessage(CREATED);
        when(messages.findByIdAndDeletedAtIsNull(MSG)).thenReturn(Optional.of(msg));
        DiscussionService svc = newService(CREATED.plus(Duration.ofMinutes(2)));

        svc.delete(SENDER, AccountRole.ROLE_ACCOUNT, APPT, MSG);

        assertThat(msg.getDeletedAt()).isNotNull();
    }

    private DiscussionMessage newMessage(Instant createdAt) {
        DiscussionMessage msg = new DiscussionMessage(
                MSG, APPT, SENDER,
                DiscussionSenderRole.PATIENT_SIDE,
                DiscussionMessageType.REPLY,
                "hello");
        setCreatedAt(msg, createdAt);
        return msg;
    }

    private static void setCreatedAt(DiscussionMessage msg, Instant at) {
        try {
            var f = DiscussionMessage.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(msg, at);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private DiscussionService newService(Instant now) {
        Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
        return new DiscussionService(messages, attachments, readMarkers, appointments, files, access, notifications, audit, fixed);
    }
}

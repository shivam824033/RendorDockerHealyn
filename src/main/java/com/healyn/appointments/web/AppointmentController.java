package com.healyn.appointments.web;

import com.healyn.appointments.domain.Appointment;
import com.healyn.appointments.domain.AppointmentStatus;
import com.healyn.appointments.service.AppointmentService;
import com.healyn.appointments.service.BookingRequest;
import com.healyn.appointments.service.FollowUpRequest;
import com.healyn.appointments.service.RescheduleRequest;
import com.healyn.appointments.service.ScheduleRequest;
import com.healyn.appointments.service.TransitionRequest;
import com.healyn.auth.domain.AccountRole;
import com.healyn.common.pagination.CursorPage;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping
    public AppointmentDtos.AppointmentPage list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "patient_id", required = false) UUID patientId,
            @RequestParam(value = "status", required = false) String statusCsv,
            @RequestParam(value = "from", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {

        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        Set<AppointmentStatus> statuses = parseStatuses(statusCsv);

        CursorPage<Appointment> page = service.list(actorId, role, patientId, statuses, from, to, cursor, limit);
        List<AppointmentDtos.AppointmentView> views =
                page.items().stream().map(AppointmentMapper::toView).toList();
        return new AppointmentDtos.AppointmentPage(views, page.nextCursor());
    }

    @GetMapping("/upcoming")
    public AppointmentDtos.AppointmentList upcoming(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "limit", required = false, defaultValue = "30") int limit) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        List<AppointmentDtos.AppointmentView> views =
                service.upcoming(actorId, role, limit).stream().map(AppointmentMapper::toView).toList();
        return new AppointmentDtos.AppointmentList(views);
    }

    @GetMapping("/calendar")
    public AppointmentDtos.AppointmentList calendar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "from")
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to")
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        List<AppointmentDtos.AppointmentView> views =
                service.calendar(actorId, role, from, to).stream().map(AppointmentMapper::toView).toList();
        return new AppointmentDtos.AppointmentList(views);
    }

    @GetMapping("/{id}")
    public AppointmentDtos.AppointmentView get(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable("id") UUID id) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        return AppointmentMapper.toView(service.get(actorId, role, id));
    }

    @PostMapping
    public ResponseEntity<AppointmentDtos.AppointmentView> book(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AppointmentDtos.BookRequest body) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        BookingRequest req = new BookingRequest(
                body.patientId(), body.requestedDate(), body.preferredTime(), body.reason());
        Appointment booked = service.book(actorId, role, req, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentMapper.toView(booked));
    }

    @PostMapping("/{id}/schedule")
    public AppointmentDtos.AppointmentView schedule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id,
            @Valid @RequestBody AppointmentDtos.ScheduleRequestBody body) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        ScheduleRequest req = new ScheduleRequest(body.scheduledAt(), body.durationMinutes());
        return AppointmentMapper.toView(service.schedule(actorId, role, id, req));
    }

    @PostMapping("/follow-ups")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentDtos.AppointmentView createFollowUp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AppointmentDtos.FollowUpRequestBody body) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        FollowUpRequest req = new FollowUpRequest(
                body.patientId(), body.scheduledAt(), body.durationMinutes(), body.reason());
        return AppointmentMapper.toView(service.createFollowUp(actorId, role, req));
    }

    @PostMapping("/{id}/transitions")
    public AppointmentDtos.AppointmentView transition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id,
            @Valid @RequestBody AppointmentDtos.TransitionRequestBody body) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        TransitionRequest req = new TransitionRequest(body.to(), body.cancelReason(), body.cancelNote());
        return AppointmentMapper.toView(service.transition(actorId, role, id, req));
    }

    @PostMapping("/{id}/reschedule")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentDtos.AppointmentView reschedule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id,
            @Valid @RequestBody AppointmentDtos.RescheduleRequestBody body) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        RescheduleRequest req = new RescheduleRequest(
                body.scheduledAt(), body.durationMinutes(),
                body.requestedDate(), body.preferredTime(), body.reason());
        return AppointmentMapper.toView(service.reschedule(actorId, role, id, req));
    }

    private static Set<AppointmentStatus> parseStatuses(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(AppointmentStatus::valueOf)
                .collect(java.util.stream.Collectors.toCollection(
                        () -> EnumSet.noneOf(AppointmentStatus.class)));
    }

    private static AccountRole roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? AccountRole.ROLE_ACCOUNT : AccountRole.valueOf(role);
    }
}

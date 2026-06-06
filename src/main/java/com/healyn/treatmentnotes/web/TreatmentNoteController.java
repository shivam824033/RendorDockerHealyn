package com.healyn.treatmentnotes.web;

import com.healyn.auth.domain.AccountRole;
import com.healyn.treatmentnotes.domain.TreatmentNote;
import com.healyn.treatmentnotes.service.TreatmentNoteService;
import com.healyn.treatmentnotes.service.UpsertTreatmentNoteRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/appointments/{appointmentId}/treatment_note")
public class TreatmentNoteController {

    private final TreatmentNoteService service;

    public TreatmentNoteController(TreatmentNoteService service) {
        this.service = service;
    }

    @GetMapping
    public TreatmentNoteDtos.TreatmentNoteView get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("appointmentId") UUID appointmentId) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        return TreatmentNoteMapper.toView(service.getForAppointment(actorId, role, appointmentId));
    }

    @PutMapping
    public TreatmentNoteDtos.TreatmentNoteView upsert(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("appointmentId") UUID appointmentId,
            @RequestBody TreatmentNoteDtos.UpsertTreatmentNoteBody body) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        UpsertTreatmentNoteRequest req = new UpsertTreatmentNoteRequest(
                body.diagnosis(), body.notes(), body.recoveryInstructions(), body.nextReviewAt());
        TreatmentNote saved = service.upsert(actorId, role, appointmentId, req);
        return TreatmentNoteMapper.toView(saved);
    }

    private static AccountRole roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? AccountRole.ROLE_ACCOUNT : AccountRole.valueOf(role);
    }
}

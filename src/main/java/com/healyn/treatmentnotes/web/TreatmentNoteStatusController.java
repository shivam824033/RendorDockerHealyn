package com.healyn.treatmentnotes.web;

import com.healyn.auth.domain.AccountRole;
import com.healyn.treatmentnotes.service.TreatmentNoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/// Bulk "which of these appointments already have a treatment note" lookup, so the
/// physiotherapist's appointment list can flag completed sessions that still need a
/// note. POST (not GET) so a list of ids travels in the body, not a long query string.
@RestController
@RequestMapping("/treatment_notes")
public class TreatmentNoteStatusController {

    private final TreatmentNoteService service;

    public TreatmentNoteStatusController(TreatmentNoteService service) {
        this.service = service;
    }

    @PostMapping("/status")
    public TreatmentNoteDtos.NoteStatusResponse status(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody TreatmentNoteDtos.NoteStatusRequest body) {
        AccountRole role = roleOf(jwt);
        Set<UUID> withNotes = service.appointmentsWithNotes(role, body.appointmentIds());
        return new TreatmentNoteDtos.NoteStatusResponse(List.copyOf(withNotes));
    }

    private static AccountRole roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? AccountRole.ROLE_ACCOUNT : AccountRole.valueOf(role);
    }
}

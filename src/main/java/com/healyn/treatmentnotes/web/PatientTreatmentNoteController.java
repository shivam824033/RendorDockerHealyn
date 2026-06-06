package com.healyn.treatmentnotes.web;

import com.healyn.auth.domain.AccountRole;
import com.healyn.common.pagination.CursorPage;
import com.healyn.treatmentnotes.domain.TreatmentNote;
import com.healyn.treatmentnotes.service.TreatmentNoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patients/{patientId}/treatment_notes")
public class PatientTreatmentNoteController {

    private final TreatmentNoteService service;

    public PatientTreatmentNoteController(TreatmentNoteService service) {
        this.service = service;
    }

    @GetMapping
    public TreatmentNoteDtos.TreatmentNotePage list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("patientId") UUID patientId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        CursorPage<TreatmentNote> page = service.listForPatient(actorId, role, patientId, cursor, limit);
        List<TreatmentNoteDtos.TreatmentNoteView> views =
                page.items().stream().map(TreatmentNoteMapper::toView).toList();
        return new TreatmentNoteDtos.TreatmentNotePage(views, page.nextCursor());
    }

    private static AccountRole roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? AccountRole.ROLE_ACCOUNT : AccountRole.valueOf(role);
    }
}

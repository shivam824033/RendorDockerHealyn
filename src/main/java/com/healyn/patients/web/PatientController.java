package com.healyn.patients.web;

import com.healyn.auth.domain.AccountRole;
import com.healyn.patients.repository.AccountPatientRepository;
import com.healyn.patients.service.NewPatientProfile;
import com.healyn.patients.service.PatientService;
import com.healyn.patients.service.PatientService.PatientWithLink;
import com.healyn.patients.service.PatientUpdate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
public class PatientController {

    private final PatientService patientService;
    private final AccountPatientRepository links;

    public PatientController(PatientService patientService, AccountPatientRepository links) {
        this.patientService = patientService;
        this.links = links;
    }

    @GetMapping
    public PatientDtos.PatientListResponse list(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        List<PatientDtos.PatientView> views = patientService.listForAccount(accountId, role).stream()
                .map(PatientMapper::toView)
                .toList();
        return new PatientDtos.PatientListResponse(views);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientDtos.PatientView create(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody PatientDtos.CreateFamilyMemberRequest body) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        NewPatientProfile profile = new NewPatientProfile(
                body.fullName(), body.dateOfBirth(), body.sex(),
                body.phoneE164(), body.email(), body.bloodGroup(), body.allergies(), body.notes());
        var patient = patientService.addFamilyMember(accountId, body.relationship(), profile);
        var link = links.findLink(accountId, patient.getId()).orElseThrow();
        return PatientMapper.toView(new PatientWithLink(patient, link));
    }

    @GetMapping("/{id}")
    public PatientDtos.PatientView get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        return PatientMapper.toView(patientService.get(accountId, role, id));
    }

    @PatchMapping("/{id}")
    public PatientDtos.PatientView update(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable("id") UUID id,
                                          @Valid @RequestBody PatientDtos.UpdatePatientRequest body) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        PatientUpdate update = new PatientUpdate(body.fullName(), body.dateOfBirth(), body.sex(),
                body.phoneE164(), body.email(), body.bloodGroup(), body.allergies(), body.notes());
        var patient = patientService.update(accountId, role, id, update);
        var link = role == AccountRole.ROLE_PHYSIO ? null : links.findLink(accountId, id).orElse(null);
        return PatientMapper.toView(new PatientWithLink(patient, link));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        patientService.removeLink(accountId, role, id);
    }

    private static AccountRole roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? AccountRole.ROLE_ACCOUNT : AccountRole.valueOf(role);
    }
}

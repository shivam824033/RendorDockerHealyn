package com.healyn.compliance.web;

import com.healyn.common.web.ClientInfo;
import com.healyn.compliance.domain.ConsentRecord;
import com.healyn.compliance.service.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/// The authenticated account's consent history, plus grant/withdraw for account-level consents
/// (re-consent when a document version changes). Family-member authority is captured at family-add.
@RestController
@RequestMapping("/me/consents")
public class ConsentController {

    private final ConsentService consents;

    public ConsentController(ConsentService consents) {
        this.consents = consents;
    }

    @GetMapping
    public ComplianceDtos.ConsentListResponse list(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        List<ComplianceDtos.ConsentView> views = consents.listForAccount(accountId).stream()
                .map(ConsentController::toView)
                .toList();
        return new ComplianceDtos.ConsentListResponse(views);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComplianceDtos.ConsentView record(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody ComplianceDtos.ConsentRequest body,
                                             HttpServletRequest http) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        String ip = ClientInfo.clientIp(http);
        String ua = ClientInfo.userAgent(http);
        ConsentRecord record = body.granted()
                ? consents.grant(accountId, null, body.consentType(), ip, ua)
                : consents.withdraw(accountId, null, body.consentType(), ip, ua);
        return toView(record);
    }

    private static ComplianceDtos.ConsentView toView(ConsentRecord r) {
        return new ComplianceDtos.ConsentView(
                r.getId(), r.getConsentType(), r.getPatientId(), r.isGranted(),
                r.getDocumentVersion(), r.getGrantedAt(), r.getWithdrawnAt());
    }
}

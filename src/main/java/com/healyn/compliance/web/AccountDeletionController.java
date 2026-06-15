package com.healyn.compliance.web;

import com.healyn.compliance.domain.AccountDeletionRequest;
import com.healyn.compliance.service.AccountDeletionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/// Account deletion / right-to-erasure. A password-confirmed request opens a cancellable grace
/// window; the scheduled sweep anonymizes the account afterwards. Cancellation requires the
/// holder to log back in (the request revokes all sessions).
@RestController
@RequestMapping("/me/deletion-request")
public class AccountDeletionController {

    private final AccountDeletionService deletions;

    public AccountDeletionController(AccountDeletionService deletions) {
        this.deletions = deletions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ComplianceDtos.DeletionRequestView request(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody ComplianceDtos.DeletionRequestBody body) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        AccountDeletionRequest req = deletions.request(accountId, body.password(), body.reason());
        return toView(req);
    }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Jwt jwt) {
        deletions.cancel(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping
    public ResponseEntity<ComplianceDtos.DeletionRequestView> active(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        return deletions.activeRequest(accountId)
                .map(req -> ResponseEntity.ok(toView(req)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static ComplianceDtos.DeletionRequestView toView(AccountDeletionRequest req) {
        return new ComplianceDtos.DeletionRequestView(
                req.getStatus().name(), req.getRequestedAt(), req.getPurgeAfter());
    }
}

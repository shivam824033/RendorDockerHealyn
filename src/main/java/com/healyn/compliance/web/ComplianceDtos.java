package com.healyn.compliance.web;

import com.healyn.compliance.domain.ConsentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ComplianceDtos {

    private ComplianceDtos() {}

    /// A versioned legal document served at {@code GET /legal/{kind}}.
    public record LegalDocumentResponse(
            String kind,
            String version,
            String locale,
            String title,
            String bodyMarkdown,
            Instant effectiveAt) {}

    /// One consent record in the account's history.
    public record ConsentView(
            UUID id,
            ConsentType consentType,
            UUID patientId,
            boolean granted,
            String documentVersion,
            Instant grantedAt,
            Instant withdrawnAt) {}

    public record ConsentListResponse(List<ConsentView> consents) {}

    /// Grant or withdraw an account-level consent (re-consent when a document changes, or
    /// withdraw a non-essential consent). Family-member authority is captured at family-add.
    public record ConsentRequest(
            @NotNull ConsentType consentType,
            @NotNull Boolean granted) {}

    /// Open an account deletion / erasure request. Re-authenticates with the account password.
    public record DeletionRequestBody(
            @NotNull @Size(min = 1, max = 128) String password,
            @Size(max = 500) String reason) {}

    /// The state of an account deletion request.
    public record DeletionRequestView(
            String status,
            Instant requestedAt,
            Instant purgeAfter) {}
}

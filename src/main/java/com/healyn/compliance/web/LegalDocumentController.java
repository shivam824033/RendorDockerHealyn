package com.healyn.compliance.web;

import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.compliance.domain.LegalDocument;
import com.healyn.compliance.domain.LegalDocumentKind;
import com.healyn.compliance.service.LegalDocumentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/// Serves the current (and historical) Privacy Policy / Terms. Public — these must be readable
/// before signup and during app-store review (SecurityConfig permits {@code /legal/**}).
@RestController
@RequestMapping("/legal")
public class LegalDocumentController {

    private final LegalDocumentService legalDocuments;

    public LegalDocumentController(LegalDocumentService legalDocuments) {
        this.legalDocuments = legalDocuments;
    }

    @GetMapping("/{kind}")
    public ComplianceDtos.LegalDocumentResponse current(@PathVariable("kind") String kind) {
        return toResponse(legalDocuments.current(parseKind(kind)));
    }

    @GetMapping("/{kind}/{version}")
    public ComplianceDtos.LegalDocumentResponse byVersion(@PathVariable("kind") String kind,
                                                          @PathVariable("version") String version) {
        return toResponse(legalDocuments.byVersion(parseKind(kind), version));
    }

    private static LegalDocumentKind parseKind(String kind) {
        try {
            return LegalDocumentKind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(ErrorCode.COMPLIANCE_LEGAL_DOCUMENT_NOT_FOUND,
                    "Unknown legal document: " + kind);
        }
    }

    private static ComplianceDtos.LegalDocumentResponse toResponse(LegalDocument doc) {
        return new ComplianceDtos.LegalDocumentResponse(
                doc.getKind().name(), doc.getVersion(), doc.getLocale(),
                doc.getTitle(), doc.getBodyMarkdown(), doc.getEffectiveAt());
    }
}

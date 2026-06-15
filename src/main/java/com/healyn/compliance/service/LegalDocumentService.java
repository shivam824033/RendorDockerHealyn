package com.healyn.compliance.service;

import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.compliance.config.ComplianceProperties;
import com.healyn.compliance.domain.LegalDocument;
import com.healyn.compliance.domain.LegalDocumentKind;
import com.healyn.compliance.repository.LegalDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Reads versioned legal documents (Privacy Policy / Terms). Documents are immutable;
/// publishing a new version is a separate operator task, out of scope for the app surface.
@Service
public class LegalDocumentService {

    private final LegalDocumentRepository documents;
    private final ComplianceProperties props;

    public LegalDocumentService(LegalDocumentRepository documents, ComplianceProperties props) {
        this.documents = documents;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public LegalDocument current(LegalDocumentKind kind) {
        return documents.findByKindAndLocaleAndCurrentIsTrue(kind, props.defaultLocale())
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.COMPLIANCE_LEGAL_DOCUMENT_NOT_FOUND, "No current document for " + kind));
    }

    @Transactional(readOnly = true)
    public LegalDocument byVersion(LegalDocumentKind kind, String version) {
        return documents.findByKindAndVersionAndLocale(kind, version, props.defaultLocale())
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.COMPLIANCE_LEGAL_DOCUMENT_NOT_FOUND,
                        "No document " + kind + " version " + version));
    }
}

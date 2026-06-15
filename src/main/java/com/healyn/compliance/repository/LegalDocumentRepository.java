package com.healyn.compliance.repository;

import com.healyn.compliance.domain.LegalDocument;
import com.healyn.compliance.domain.LegalDocumentKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

    Optional<LegalDocument> findByKindAndLocaleAndCurrentIsTrue(LegalDocumentKind kind, String locale);

    Optional<LegalDocument> findByKindAndVersionAndLocale(LegalDocumentKind kind, String version, String locale);
}

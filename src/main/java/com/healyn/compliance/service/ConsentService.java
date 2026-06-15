package com.healyn.compliance.service;

import com.healyn.audit.domain.AuditAction;
import com.healyn.audit.domain.AuditResource;
import com.healyn.audit.service.AuditLogger;
import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.common.id.UuidV7;
import com.healyn.compliance.config.ComplianceProperties;
import com.healyn.compliance.domain.ConsentRecord;
import com.healyn.compliance.domain.ConsentType;
import com.healyn.compliance.domain.LegalDocument;
import com.healyn.compliance.domain.LegalDocumentKind;
import com.healyn.compliance.repository.ConsentRecordRepository;
import com.healyn.compliance.repository.LegalDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// Records demonstrable consent (DPDP Act 2023). The trail is append-only: a grant and a
/// withdrawal are separate rows. Terms / Privacy consents snapshot the current published
/// legal-document version so we can prove exactly what text was agreed to.
@Service
public class ConsentService {

    /// Account-level consents captured at registration, in display order.
    private static final List<ConsentType> REGISTRATION_CONSENTS = List.of(
            ConsentType.TERMS_OF_SERVICE,
            ConsentType.PRIVACY_POLICY,
            ConsentType.HEALTH_DATA_PROCESSING);

    private final ConsentRecordRepository consents;
    private final LegalDocumentRepository documents;
    private final AccountRepository accounts;
    private final AuditLogger audit;
    private final ComplianceProperties props;
    private final Clock clock;

    public ConsentService(ConsentRecordRepository consents, LegalDocumentRepository documents,
                          AccountRepository accounts, AuditLogger audit,
                          ComplianceProperties props, Clock clock) {
        this.consents = consents;
        this.documents = documents;
        this.accounts = accounts;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public void recordRegistrationConsents(UUID accountId, String ipAddress, String userAgent) {
        for (ConsentType type : REGISTRATION_CONSENTS) {
            grant(accountId, null, type, ipAddress, userAgent);
        }
    }

    @Transactional
    public void recordFamilyAuthority(UUID accountId, UUID patientId, String ipAddress, String userAgent) {
        grant(accountId, patientId, ConsentType.FAMILY_MEMBER_AUTHORITY, ipAddress, userAgent);
    }

    @Transactional
    public ConsentRecord grant(UUID accountId, UUID patientId, ConsentType type,
                               String ipAddress, String userAgent) {
        Instant now = Instant.now(clock);
        LegalDocument doc = currentDocumentFor(type);
        ConsentRecord record = ConsentRecord.granted(
                UuidV7.generate(), accountId, patientId, type,
                doc == null ? null : doc.getId(),
                doc == null ? null : doc.getVersion(),
                now, ipAddress, userAgent);
        consents.save(record);
        audit(AuditAction.CONSENT_GRANT, accountId, record.getId(), type, patientId);
        return record;
    }

    @Transactional
    public ConsentRecord withdraw(UUID accountId, UUID patientId, ConsentType type,
                                  String ipAddress, String userAgent) {
        Instant now = Instant.now(clock);
        ConsentRecord record = ConsentRecord.withdrawn(
                UuidV7.generate(), accountId, patientId, type, now, ipAddress, userAgent);
        consents.save(record);
        audit(AuditAction.CONSENT_WITHDRAW, accountId, record.getId(), type, patientId);
        return record;
    }

    @Transactional(readOnly = true)
    public List<ConsentRecord> listForAccount(UUID accountId) {
        return consents.findByAccountIdOrderByGrantedAtDesc(accountId);
    }

    /// The current legal document a consent type binds to, or null for consent types with
    /// no document (health-data processing, family-member authority).
    private LegalDocument currentDocumentFor(ConsentType type) {
        LegalDocumentKind kind = switch (type) {
            case TERMS_OF_SERVICE -> LegalDocumentKind.TERMS_OF_SERVICE;
            case PRIVACY_POLICY -> LegalDocumentKind.PRIVACY_POLICY;
            case HEALTH_DATA_PROCESSING, FAMILY_MEMBER_AUTHORITY -> null;
        };
        if (kind == null) return null;
        // Bind to the current document if one is published; absence is non-fatal
        // (the consent is still recorded, just without a version snapshot).
        return documents.findByKindAndLocaleAndCurrentIsTrue(kind, props.defaultLocale()).orElse(null);
    }

    private void audit(AuditAction action, UUID accountId, UUID consentId, ConsentType type, UUID patientId) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("consent_type", type.name());
        if (patientId != null) metadata.put("patient_id", patientId.toString());
        AccountRole role = accounts.findById(accountId).map(Account::getRole).orElse(null);
        audit.record(action, accountId, role, AuditResource.CONSENT, consentId, metadata);
    }
}

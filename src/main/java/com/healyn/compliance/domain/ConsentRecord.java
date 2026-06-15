package com.healyn.compliance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/// A demonstrable consent record (DPDP Act 2023). One row per grant or withdrawal — the
/// trail is append-only, so a withdrawal is a new {@code granted=false} row rather than an
/// edit. Account-level consents carry a null {@code patientId}; a
/// {@code FAMILY_MEMBER_AUTHORITY} consent is keyed to the managed patient.
@Entity
@Table(name = "consent_records")
public class ConsentRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "patient_id", updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "consent_type", nullable = false, updatable = false, columnDefinition = "consent_type")
    private ConsentType consentType;

    @Column(name = "legal_document_id", updatable = false)
    private UUID legalDocumentId;

    @Column(name = "document_version", updatable = false)
    private String documentVersion;

    @Column(name = "granted", nullable = false, updatable = false)
    private boolean granted;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "withdrawn_at", updatable = false)
    private Instant withdrawnAt;

    @Column(name = "ip_address", updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected ConsentRecord() {}

    private ConsentRecord(UUID id, UUID accountId, UUID patientId, ConsentType consentType,
                          UUID legalDocumentId, String documentVersion, boolean granted,
                          Instant when, String ipAddress, String userAgent) {
        this.id = id;
        this.accountId = accountId;
        this.patientId = patientId;
        this.consentType = consentType;
        this.legalDocumentId = legalDocumentId;
        this.documentVersion = documentVersion;
        this.granted = granted;
        this.grantedAt = when;
        this.withdrawnAt = granted ? null : when;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public static ConsentRecord granted(UUID id, UUID accountId, UUID patientId, ConsentType type,
                                        UUID legalDocumentId, String documentVersion, Instant when,
                                        String ipAddress, String userAgent) {
        return new ConsentRecord(id, accountId, patientId, type, legalDocumentId, documentVersion,
                true, when, ipAddress, userAgent);
    }

    public static ConsentRecord withdrawn(UUID id, UUID accountId, UUID patientId, ConsentType type,
                                          Instant when, String ipAddress, String userAgent) {
        return new ConsentRecord(id, accountId, patientId, type, null, null,
                false, when, ipAddress, userAgent);
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public UUID getPatientId() { return patientId; }
    public ConsentType getConsentType() { return consentType; }
    public UUID getLegalDocumentId() { return legalDocumentId; }
    public String getDocumentVersion() { return documentVersion; }
    public boolean isGranted() { return granted; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
}

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

/// A versioned legal document (Privacy Policy / Terms). Immutable once written: a new
/// version is a new row, and the partial unique index {@code idx_legal_documents_current}
/// guarantees at most one current row per (kind, locale). Standalone entity — it has no
/// {@code updated_at}, so it does not extend {@code BaseEntity}.
@Entity
@Table(name = "legal_documents")
public class LegalDocument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, columnDefinition = "legal_document_kind")
    private LegalDocumentKind kind;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "locale", nullable = false)
    private String locale;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body_markdown", nullable = false)
    private String bodyMarkdown;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected LegalDocument() {}

    public UUID getId() { return id; }
    public LegalDocumentKind getKind() { return kind; }
    public String getVersion() { return version; }
    public String getLocale() { return locale; }
    public String getTitle() { return title; }
    public String getBodyMarkdown() { return bodyMarkdown; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public boolean isCurrent() { return current; }
    public Instant getCreatedAt() { return createdAt; }
}

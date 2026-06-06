package com.healyn.files.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_objects")
public class FileObject {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_account_id", nullable = false, updatable = false)
    private UUID ownerAccountId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, updatable = false, columnDefinition = "file_kind")
    private FileKind kind;

    @Column(name = "mime_type", nullable = false, updatable = false)
    private String mimeType;

    @Column(name = "original_filename", nullable = false, updatable = false)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256_hex", length = 64)
    private String sha256Hex;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "file_status")
    private FileStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "available_at")
    private Instant availableAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected FileObject() {}

    public FileObject(UUID id,
                      UUID ownerAccountId,
                      UUID patientId,
                      FileKind kind,
                      String mimeType,
                      String originalFilename,
                      String storageKey,
                      long sizeBytes) {
        this.id = id;
        this.ownerAccountId = ownerAccountId;
        this.patientId = patientId;
        this.kind = kind;
        this.mimeType = mimeType;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.status = FileStatus.PENDING_UPLOAD;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerAccountId() { return ownerAccountId; }
    public UUID getPatientId() { return patientId; }
    public FileKind getKind() { return kind; }
    public String getMimeType() { return mimeType; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStorageKey() { return storageKey; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256Hex() { return sha256Hex; }
    public FileStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void markAvailable(Instant now, String sha256Hex) {
        this.status = FileStatus.AVAILABLE;
        this.availableAt = now;
        this.sha256Hex = sha256Hex;
    }

    public void quarantine() {
        this.status = FileStatus.QUARANTINED;
    }

    public void softDelete(Instant now) {
        this.status = FileStatus.DELETED;
        this.deletedAt = now;
    }
}

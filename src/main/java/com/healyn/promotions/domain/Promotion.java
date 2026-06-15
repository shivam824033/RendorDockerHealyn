package com.healyn.promotions.domain;

import com.healyn.common.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/// A single piece of first-party clinic content (service card / banner / announcement /
/// health tip) authored by the physiotherapist (FEATURE_ROADMAP F1.23). Visible to a
/// patient when {@code active}, not soft-deleted, and within its optional schedule
/// window. Not clinical PHI; soft-deleted via {@link #deletedAt}. The cover image lives
/// in object storage under {@link #coverObjectKey}, mirroring {@code PhysioProfile}'s
/// avatar.
@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /// Phase-3 multi-clinic enabler (F3.4); null and unexposed in Phase 1.
    @Column(name = "clinic_id")
    private UUID clinicId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "short_description", length = 280)
    private String shortDescription;

    @Column(name = "long_description")
    private String longDescription;

    @Column(name = "service_category", length = 80)
    private String serviceCategory;

    @Column(name = "cta_text", length = 40)
    private String ctaText;

    @Enumerated(EnumType.STRING)
    @Column(name = "cta_action", nullable = false, length = 32)
    private PromotionAction ctaAction = PromotionAction.NONE;

    @Column(name = "cover_object_key", length = 512)
    private String coverObjectKey;

    @Column(name = "cover_mime", length = 64)
    private String coverMime;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Promotion() {}

    public Promotion(UUID createdBy, String title) {
        this.id = UuidV7.generate();
        this.createdBy = createdBy;
        this.title = title;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /// Whether this promotion should be shown to a patient at {@code now}: active, not
    /// deleted, and within its (optional) schedule window.
    public boolean isVisibleAt(Instant now) {
        return active
                && deletedAt == null
                && (startsAt == null || !now.isBefore(startsAt))
                && (endsAt == null || now.isBefore(endsAt));
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() { return id; }
    public UUID getClinicId() { return clinicId; }
    public UUID getCreatedBy() { return createdBy; }
    public String getTitle() { return title; }
    public String getShortDescription() { return shortDescription; }
    public String getLongDescription() { return longDescription; }
    public String getServiceCategory() { return serviceCategory; }
    public String getCtaText() { return ctaText; }
    public PromotionAction getCtaAction() { return ctaAction; }
    public String getCoverObjectKey() { return coverObjectKey; }
    public String getCoverMime() { return coverMime; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setTitle(String v) { this.title = v; }
    public void setShortDescription(String v) { this.shortDescription = v; }
    public void setLongDescription(String v) { this.longDescription = v; }
    public void setServiceCategory(String v) { this.serviceCategory = v; }
    public void setCtaText(String v) { this.ctaText = v; }
    public void setCtaAction(PromotionAction v) { this.ctaAction = v; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
    public void setActive(boolean v) { this.active = v; }
    public void setStartsAt(Instant v) { this.startsAt = v; }
    public void setEndsAt(Instant v) { this.endsAt = v; }

    public void setCover(String objectKey, String mime) {
        this.coverObjectKey = objectKey;
        this.coverMime = mime;
    }
}

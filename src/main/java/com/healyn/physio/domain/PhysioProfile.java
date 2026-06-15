package com.healyn.physio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/// The single physiotherapist's public-facing profile, keyed 1:1 by the physio's
/// {@code accountId} (single-tenant — PROJECT_CONTEXT §5.2). Holds personal,
/// clinic, and social details plus an avatar object-storage key. Not clinical PHI,
/// so no soft-delete (mirrors {@code AccountAddress}). All fields are optional and
/// filled in progressively from the physiotherapist's profile editor.
@Entity
@Table(name = "physio_profiles")
public class PhysioProfile {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(name = "qualification", length = 160)
    private String qualification;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "specialization", length = 160)
    private String specialization;

    @Column(name = "bio")
    private String bio;

    @Column(name = "clinic_name", length = 160)
    private String clinicName;

    @Column(name = "clinic_address")
    private String clinicAddress;

    @Column(name = "clinic_contact_phone", length = 20)
    private String clinicContactPhone;

    @Column(name = "clinic_description")
    private String clinicDescription;

    @Column(name = "instagram_url", length = 512)
    private String instagramUrl;

    @Column(name = "facebook_url", length = 512)
    private String facebookUrl;

    @Column(name = "linkedin_url", length = 512)
    private String linkedinUrl;

    @Column(name = "website_url", length = 512)
    private String websiteUrl;

    @Column(name = "avatar_object_key", length = 512)
    private String avatarObjectKey;

    @Column(name = "avatar_mime", length = 64)
    private String avatarMime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PhysioProfile() {}

    public PhysioProfile(UUID accountId) {
        this.accountId = accountId;
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

    public UUID getAccountId() { return accountId; }
    public String getDisplayName() { return displayName; }
    public String getQualification() { return qualification; }
    public Integer getExperienceYears() { return experienceYears; }
    public String getSpecialization() { return specialization; }
    public String getBio() { return bio; }
    public String getClinicName() { return clinicName; }
    public String getClinicAddress() { return clinicAddress; }
    public String getClinicContactPhone() { return clinicContactPhone; }
    public String getClinicDescription() { return clinicDescription; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getFacebookUrl() { return facebookUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getAvatarObjectKey() { return avatarObjectKey; }
    public String getAvatarMime() { return avatarMime; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDisplayName(String v) { this.displayName = v; }
    public void setQualification(String v) { this.qualification = v; }
    public void setExperienceYears(Integer v) { this.experienceYears = v; }
    public void setSpecialization(String v) { this.specialization = v; }
    public void setBio(String v) { this.bio = v; }
    public void setClinicName(String v) { this.clinicName = v; }
    public void setClinicAddress(String v) { this.clinicAddress = v; }
    public void setClinicContactPhone(String v) { this.clinicContactPhone = v; }
    public void setClinicDescription(String v) { this.clinicDescription = v; }
    public void setInstagramUrl(String v) { this.instagramUrl = v; }
    public void setFacebookUrl(String v) { this.facebookUrl = v; }
    public void setLinkedinUrl(String v) { this.linkedinUrl = v; }
    public void setWebsiteUrl(String v) { this.websiteUrl = v; }

    public void setAvatar(String objectKey, String mime) {
        this.avatarObjectKey = objectKey;
        this.avatarMime = mime;
    }
}

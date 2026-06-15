package com.healyn.physio.service;

import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.UnprocessableException;
import com.healyn.common.id.UuidV7;
import com.healyn.files.config.HealynS3Properties;
import com.healyn.files.domain.FileMime;
import com.healyn.files.port.FileStorePort;
import com.healyn.physio.domain.PhysioProfile;
import com.healyn.physio.policy.PhysioProfilePolicy;
import com.healyn.physio.repository.PhysioProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// Owns the single physiotherapist's profile (PROJECT_CONTEXT §5.2): read by every
/// patient, edited only by the physiotherapist. The avatar reuses the object-storage
/// presign mechanism ({@link FileStorePort} + {@link FileMime}) under a dedicated
/// {@code physio/<accountId>/avatar/} key prefix — it is never written to the
/// patient-scoped {@code file_objects} table. Presigned URLs use the shared ≤5-minute
/// TTL (CLAUDE.md hard rule #5).
@Service
public class PhysioProfileService {

    private static final int MAGIC_PROBE_BYTES = 8 * 1024;
    // Avatars are images only — no PDF.
    private static final Set<FileMime> AVATAR_MIMES = Set.of(FileMime.JPEG, FileMime.PNG);

    private final PhysioProfileRepository profiles;
    private final PhysioProfilePolicy policy;
    private final FileStorePort store;
    private final Duration presignTtl;

    public PhysioProfileService(PhysioProfileRepository profiles, PhysioProfilePolicy policy,
                                FileStorePort store, HealynS3Properties s3) {
        this.profiles = profiles;
        this.policy = policy;
        this.store = store;
        this.presignTtl = Duration.ofSeconds(s3.presignTtlSeconds());
    }

    /// The single profile, if one has been created yet. Readable by any authenticated
    /// account — there is one physiotherapist, shown to every patient.
    @Transactional(readOnly = true)
    public Optional<PhysioProfile> find() {
        return profiles.findTopByOrderByUpdatedAtDesc();
    }

    /// A fresh presigned GET URL for the avatar, or empty when none is set.
    @Transactional(readOnly = true)
    public Optional<String> avatarUrl(PhysioProfile profile) {
        if (profile == null || profile.getAvatarObjectKey() == null) return Optional.empty();
        return Optional.of(store.presignGet(profile.getAvatarObjectKey(), "avatar", presignTtl));
    }

    public long presignTtlSeconds() {
        return presignTtl.toSeconds();
    }

    /// Creates or updates the physiotherapist's profile. A null field is left
    /// unchanged; a blank field clears the value.
    @Transactional
    public PhysioProfile update(UUID physioAccountId, AccountRole role, PhysioProfileUpdate u) {
        policy.requirePhysio(role);
        PhysioProfile p = profiles.findById(physioAccountId)
                .orElseGet(() -> profiles.save(new PhysioProfile(physioAccountId)));
        if (u.displayName() != null) p.setDisplayName(blankToNull(u.displayName()));
        if (u.qualification() != null) p.setQualification(blankToNull(u.qualification()));
        if (u.experienceYears() != null) p.setExperienceYears(u.experienceYears());
        if (u.specialization() != null) p.setSpecialization(blankToNull(u.specialization()));
        if (u.bio() != null) p.setBio(blankToNull(u.bio()));
        if (u.clinicName() != null) p.setClinicName(blankToNull(u.clinicName()));
        if (u.clinicAddress() != null) p.setClinicAddress(blankToNull(u.clinicAddress()));
        if (u.clinicContactPhone() != null) p.setClinicContactPhone(blankToNull(u.clinicContactPhone()));
        if (u.clinicDescription() != null) p.setClinicDescription(blankToNull(u.clinicDescription()));
        if (u.instagramUrl() != null) p.setInstagramUrl(blankToNull(u.instagramUrl()));
        if (u.facebookUrl() != null) p.setFacebookUrl(blankToNull(u.facebookUrl()));
        if (u.linkedinUrl() != null) p.setLinkedinUrl(blankToNull(u.linkedinUrl()));
        if (u.websiteUrl() != null) p.setWebsiteUrl(blankToNull(u.websiteUrl()));
        return p;
    }

    /// Reserves an avatar object key and presigns its upload. Images only; size capped
    /// by the type. The key embeds the physiotherapist's account id so confirm can
    /// verify ownership without trusting client input.
    @Transactional(readOnly = true)
    public AvatarPresign presignAvatar(UUID physioAccountId, AccountRole role, String mimeType, long sizeBytes) {
        policy.requirePhysio(role);
        FileMime mime = imageMime(mimeType);
        if (sizeBytes <= 0 || sizeBytes > mime.maxBytes()) {
            throw new UnprocessableException(ErrorCode.PHYSIO_AVATAR_TOO_LARGE,
                    "size_bytes must be between 1 and " + mime.maxBytes() + " for " + mime.mimeType());
        }
        String key = "physio/%s/avatar/%s.%s".formatted(physioAccountId, UuidV7.generate(), mime.extension());
        String url = store.presignPut(key, mime.mimeType(), presignTtl);
        return new AvatarPresign(key, url, mime.mimeType(), presignTtl.toSeconds());
    }

    /// Verifies an uploaded avatar (presence + magic bytes), then sets it on the profile
    /// and removes the previous object. [objectKey] must be under the caller's own avatar
    /// prefix — a key for another path or account is rejected.
    @Transactional
    public PhysioProfile confirmAvatar(UUID physioAccountId, AccountRole role, String objectKey, String mimeType) {
        policy.requirePhysio(role);
        FileMime mime = imageMime(mimeType);
        String expectedPrefix = "physio/%s/avatar/".formatted(physioAccountId);
        if (objectKey == null || !objectKey.startsWith(expectedPrefix)) {
            throw new UnprocessableException(ErrorCode.PHYSIO_AVATAR_KEY_INVALID,
                    "object_key does not belong to this profile");
        }
        if (store.objectSize(objectKey).isEmpty()) {
            throw new UnprocessableException(ErrorCode.PHYSIO_AVATAR_INVALID,
                    "No uploaded object found for this avatar");
        }
        byte[] head = store.read(objectKey, MAGIC_PROBE_BYTES);
        if (!mime.matchesMagic(head)) {
            throw new UnprocessableException(ErrorCode.PHYSIO_AVATAR_INVALID,
                    "Uploaded file content does not match its declared type");
        }

        PhysioProfile p = profiles.findById(physioAccountId)
                .orElseGet(() -> profiles.save(new PhysioProfile(physioAccountId)));
        String previous = p.getAvatarObjectKey();
        p.setAvatar(objectKey, mime.mimeType());
        if (previous != null && !previous.equals(objectKey)) {
            store.delete(previous);
        }
        return p;
    }

    // ---- helpers ----

    private static FileMime imageMime(String mimeType) {
        FileMime mime = FileMime.fromMimeType(mimeType).filter(AVATAR_MIMES::contains).orElse(null);
        if (mime == null) {
            throw new UnprocessableException(ErrorCode.PHYSIO_AVATAR_UNSUPPORTED_MIME,
                    "Unsupported avatar type; allowed: image/jpeg, image/png");
        }
        return mime;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

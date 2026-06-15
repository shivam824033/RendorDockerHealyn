package com.healyn.promotions.service;

import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnprocessableException;
import com.healyn.common.id.UuidV7;
import com.healyn.files.config.HealynS3Properties;
import com.healyn.files.domain.FileMime;
import com.healyn.files.port.FileStorePort;
import com.healyn.promotions.config.PromotionProperties;
import com.healyn.promotions.domain.Promotion;
import com.healyn.promotions.domain.PromotionAction;
import com.healyn.promotions.policy.PromotionPolicy;
import com.healyn.promotions.repository.PromotionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// Owns clinic promotions (FEATURE_ROADMAP F1.23): the physiotherapist authors them,
/// every patient reads the active in-window set. Mutations are physio-gated via
/// {@link PromotionPolicy} (CLAUDE.md hard rule #2). Cover images reuse the object-storage
/// presign mechanism ({@link FileStorePort} + {@link FileMime}) under a dedicated
/// {@code promotions/<id>/cover/} key prefix — never the patient-scoped
/// {@code file_objects} table. Presigned URLs use the shared ≤5-minute TTL (hard rule #5).
@Service
public class PromotionService {

    private static final int MAGIC_PROBE_BYTES = 16 * 1024;
    // Cover images only — JPEG / PNG / WEBP (FILE_STORAGE_GUIDELINES §1).
    private static final Set<FileMime> COVER_MIMES = Set.of(FileMime.JPEG, FileMime.PNG, FileMime.WEBP);

    private final PromotionRepository promotions;
    private final PromotionPolicy policy;
    private final FileStorePort store;
    private final Duration presignTtl;
    private final int maxActive;

    public PromotionService(PromotionRepository promotions, PromotionPolicy policy,
                            FileStorePort store, HealynS3Properties s3, PromotionProperties props) {
        this.promotions = promotions;
        this.policy = policy;
        this.store = store;
        this.presignTtl = Duration.ofSeconds(s3.presignTtlSeconds());
        this.maxActive = props.maxActive();
    }

    public long presignTtlSeconds() {
        return presignTtl.toSeconds();
    }

    // ---- reads ----

    /// Active, in-window promotions for the patient surface, in display order.
    @Transactional(readOnly = true)
    public List<Promotion> listVisible() {
        return promotions.findVisible(Instant.now());
    }

    /// Every non-deleted promotion for the physiotherapist's management view.
    @Transactional(readOnly = true)
    public List<Promotion> listForManagement(AccountRole role) {
        policy.requirePhysio(role);
        return promotions.findByDeletedAtIsNullOrderByDisplayOrderAscCreatedAtDesc();
    }

    /// A fresh presigned GET URL for the cover, or empty when none is set.
    @Transactional(readOnly = true)
    public Optional<String> coverUrl(Promotion p) {
        if (p == null || p.getCoverObjectKey() == null) return Optional.empty();
        return Optional.of(store.presignGet(p.getCoverObjectKey(), "cover", presignTtl));
    }

    // ---- writes ----

    @Transactional
    public Promotion create(AccountRole role, UUID physioAccountId, NewPromotion cmd) {
        policy.requirePhysio(role);
        boolean active = cmd.active() == null || cmd.active();
        if (active) {
            requireActiveCapacity();
        }
        validateSchedule(cmd.startsAt(), cmd.endsAt());

        Promotion p = new Promotion(physioAccountId, requireTitle(cmd.title()));
        p.setShortDescription(blankToNull(cmd.shortDescription()));
        p.setLongDescription(blankToNull(cmd.longDescription()));
        p.setServiceCategory(blankToNull(cmd.serviceCategory()));
        p.setCtaText(blankToNull(cmd.ctaText()));
        p.setCtaAction(cmd.ctaAction() == null ? PromotionAction.NONE : cmd.ctaAction());
        p.setStartsAt(cmd.startsAt());
        p.setEndsAt(cmd.endsAt());
        p.setActive(active);
        Integer max = promotions.findMaxDisplayOrder();
        p.setDisplayOrder(max == null ? 0 : max + 1);
        return promotions.save(p);
    }

    @Transactional
    public Promotion update(AccountRole role, UUID id, PromotionUpdate cmd) {
        policy.requirePhysio(role);
        validateSchedule(cmd.startsAt(), cmd.endsAt());
        Promotion p = require(id);
        p.setTitle(requireTitle(cmd.title()));
        p.setShortDescription(blankToNull(cmd.shortDescription()));
        p.setLongDescription(blankToNull(cmd.longDescription()));
        p.setServiceCategory(blankToNull(cmd.serviceCategory()));
        p.setCtaText(blankToNull(cmd.ctaText()));
        p.setCtaAction(cmd.ctaAction() == null ? PromotionAction.NONE : cmd.ctaAction());
        p.setStartsAt(cmd.startsAt());
        p.setEndsAt(cmd.endsAt());
        return p;
    }

    @Transactional
    public Promotion setActive(AccountRole role, UUID id, boolean active) {
        policy.requirePhysio(role);
        Promotion p = require(id);
        if (active && !p.isActive()) {
            requireActiveCapacity();
        }
        p.setActive(active);
        return p;
    }

    /// Reorders the supplied promotions so display priority follows list position (index
    /// 0 first). [orderedIds] must be exactly the current set of non-deleted promotions.
    @Transactional
    public List<Promotion> reorder(AccountRole role, List<UUID> orderedIds) {
        policy.requirePhysio(role);
        List<Promotion> current = promotions.findByDeletedAtIsNullOrderByDisplayOrderAscCreatedAtDesc();
        Set<UUID> expected = new HashSet<>();
        for (Promotion p : current) expected.add(p.getId());
        if (orderedIds.size() != expected.size() || !new HashSet<>(orderedIds).equals(expected)) {
            throw new UnprocessableException(ErrorCode.PROMOTION_REORDER_MISMATCH,
                    "ordered_ids must list every current promotion exactly once");
        }
        List<Promotion> byId = new ArrayList<>(current);
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID id = orderedIds.get(i);
            for (Promotion p : byId) {
                if (p.getId().equals(id)) {
                    p.setDisplayOrder(i);
                    break;
                }
            }
        }
        return promotions.findByDeletedAtIsNullOrderByDisplayOrderAscCreatedAtDesc();
    }

    @Transactional
    public void delete(AccountRole role, UUID id) {
        policy.requirePhysio(role);
        Promotion p = require(id);
        String cover = p.getCoverObjectKey();
        p.softDelete();
        if (cover != null) {
            store.delete(cover);
        }
    }

    // ---- cover image ----

    /// Reserves a cover object key and presigns its upload. Images only (JPEG/PNG/WEBP);
    /// size capped by the type. The key embeds the promotion id so confirm can verify
    /// ownership without trusting client input.
    @Transactional(readOnly = true)
    public CoverPresign presignCover(AccountRole role, UUID id, String mimeType, long sizeBytes) {
        policy.requirePhysio(role);
        require(id); // 404 before handing out an upload URL for a missing promotion
        FileMime mime = imageMime(mimeType);
        if (sizeBytes <= 0 || sizeBytes > mime.maxBytes()) {
            throw new UnprocessableException(ErrorCode.PROMOTION_COVER_TOO_LARGE,
                    "size_bytes must be between 1 and " + mime.maxBytes() + " for " + mime.mimeType());
        }
        String key = "promotions/%s/cover/%s.%s".formatted(id, UuidV7.generate(), mime.extension());
        String url = store.presignPut(key, mime.mimeType(), presignTtl);
        return new CoverPresign(key, url, mime.mimeType(), presignTtl.toSeconds());
    }

    /// Verifies an uploaded cover (presence + magic bytes), sets it on the promotion, and
    /// removes the previous object. [objectKey] must be under this promotion's own cover
    /// prefix — a key for another path or promotion is rejected.
    @Transactional
    public Promotion confirmCover(AccountRole role, UUID id, String objectKey, String mimeType) {
        policy.requirePhysio(role);
        Promotion p = require(id);
        FileMime mime = imageMime(mimeType);
        String expectedPrefix = "promotions/%s/cover/".formatted(id);
        if (objectKey == null || !objectKey.startsWith(expectedPrefix)) {
            throw new UnprocessableException(ErrorCode.PROMOTION_COVER_KEY_INVALID,
                    "object_key does not belong to this promotion");
        }
        if (store.objectSize(objectKey).isEmpty()) {
            throw new UnprocessableException(ErrorCode.PROMOTION_COVER_INVALID,
                    "No uploaded object found for this cover");
        }
        byte[] head = store.read(objectKey, MAGIC_PROBE_BYTES);
        if (!mime.matchesMagic(head)) {
            throw new UnprocessableException(ErrorCode.PROMOTION_COVER_INVALID,
                    "Uploaded file content does not match its declared type");
        }
        String previous = p.getCoverObjectKey();
        p.setCover(objectKey, mime.mimeType());
        if (previous != null && !previous.equals(objectKey)) {
            store.delete(previous);
        }
        return p;
    }

    // ---- helpers ----

    private Promotion require(UUID id) {
        return promotions.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PROMOTION_NOT_FOUND, "Promotion not found"));
    }

    private void requireActiveCapacity() {
        if (promotions.countByActiveTrueAndDeletedAtIsNull() >= maxActive) {
            throw new UnprocessableException(ErrorCode.PROMOTION_LIMIT_REACHED,
                    "At most " + maxActive + " promotions can be active at once");
        }
    }

    private static void validateSchedule(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new UnprocessableException(ErrorCode.PROMOTION_INVALID_SCHEDULE,
                    "ends_at must be after starts_at");
        }
    }

    private static String requireTitle(String title) {
        String t = blankToNull(title);
        if (t == null) {
            throw new UnprocessableException(ErrorCode.UNPROCESSABLE, "title is required");
        }
        return t;
    }

    private static FileMime imageMime(String mimeType) {
        FileMime mime = FileMime.fromMimeType(mimeType).filter(COVER_MIMES::contains).orElse(null);
        if (mime == null) {
            throw new UnprocessableException(ErrorCode.PROMOTION_COVER_UNSUPPORTED_MIME,
                    "Unsupported cover type; allowed: image/jpeg, image/png, image/webp");
        }
        return mime;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

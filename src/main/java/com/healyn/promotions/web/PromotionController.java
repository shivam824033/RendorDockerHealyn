package com.healyn.promotions.web;

import com.healyn.auth.domain.AccountRole;
import com.healyn.promotions.domain.Promotion;
import com.healyn.promotions.service.CoverPresign;
import com.healyn.promotions.service.NewPromotion;
import com.healyn.promotions.service.PromotionService;
import com.healyn.promotions.service.PromotionUpdate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/// Clinic promotions (FEATURE_ROADMAP F1.23). {@code GET /promotions} is open to any
/// authenticated account (every patient is shown the one clinic's content); the
/// management and mutating endpoints are guarded in the service via
/// {@code PromotionPolicy} (CLAUDE.md hard rule #2).
@RestController
@RequestMapping("/promotions")
public class PromotionController {

    private final PromotionService service;

    public PromotionController(PromotionService service) {
        this.service = service;
    }

    // ---- patient read ----

    @GetMapping
    public PromotionDtos.PromotionListResponse listForPatient() {
        List<PromotionDtos.PromotionView> views = service.listVisible().stream()
                .map(p -> PromotionMapper.toPatientView(p, service.coverUrl(p).orElse(null)))
                .toList();
        return new PromotionDtos.PromotionListResponse(views);
    }

    // ---- physio management ----

    @GetMapping("/manage")
    public PromotionDtos.ManageListResponse listForManagement(@AuthenticationPrincipal Jwt jwt) {
        List<PromotionDtos.ManageView> views = service.listForManagement(roleOf(jwt)).stream()
                .map(this::manage)
                .toList();
        return new PromotionDtos.ManageListResponse(views);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionDtos.ManageView create(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody PromotionDtos.CreateRequest body) {
        Promotion p = service.create(roleOf(jwt), UUID.fromString(jwt.getSubject()), new NewPromotion(
                body.title(), body.shortDescription(), body.longDescription(), body.serviceCategory(),
                body.ctaText(), body.ctaAction(), body.startsAt(), body.endsAt(), body.active()));
        return manage(p);
    }

    @PatchMapping("/{id}")
    public PromotionDtos.ManageView update(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody PromotionDtos.UpdateRequest body) {
        Promotion p = service.update(roleOf(jwt), id, new PromotionUpdate(
                body.title(), body.shortDescription(), body.longDescription(), body.serviceCategory(),
                body.ctaText(), body.ctaAction(), body.startsAt(), body.endsAt()));
        return manage(p);
    }

    @PostMapping("/{id}/active")
    public PromotionDtos.ManageView setActive(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID id,
                                              @Valid @RequestBody PromotionDtos.SetActiveRequest body) {
        return manage(service.setActive(roleOf(jwt), id, body.active()));
    }

    @PostMapping("/reorder")
    public PromotionDtos.ManageListResponse reorder(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody PromotionDtos.ReorderRequest body) {
        List<PromotionDtos.ManageView> views = service.reorder(roleOf(jwt), body.orderedIds()).stream()
                .map(this::manage)
                .toList();
        return new PromotionDtos.ManageListResponse(views);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(roleOf(jwt), id);
    }

    // ---- cover image ----

    @PostMapping("/{id}/cover/presign")
    public PromotionDtos.CoverPresignView presignCover(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody PromotionDtos.CoverPresignRequest body) {
        CoverPresign c = service.presignCover(roleOf(jwt), id, body.mimeType(), body.sizeBytes());
        return new PromotionDtos.CoverPresignView(c.objectKey(), c.url(), c.contentType(), c.expiresInSeconds());
    }

    @PostMapping("/{id}/cover/confirm")
    public PromotionDtos.ManageView confirmCover(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody PromotionDtos.CoverConfirmRequest body) {
        return manage(service.confirmCover(roleOf(jwt), id, body.objectKey(), body.mimeType()));
    }

    // ---- helpers ----

    private PromotionDtos.ManageView manage(Promotion p) {
        return PromotionMapper.toManageView(p, service.coverUrl(p).orElse(null));
    }

    private static AccountRole roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? AccountRole.ROLE_ACCOUNT : AccountRole.valueOf(role);
    }
}

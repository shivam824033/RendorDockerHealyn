package com.healyn.promotions.web;

import com.healyn.promotions.domain.PromotionAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PromotionDtos {

    private PromotionDtos() {}

    /// `GET /promotions` — what a patient sees. No internal fields (active flag, schedule
    /// window, audit timestamps, clinic id) are exposed. [coverUrl] is a short-lived
    /// presigned GET URL (null when no cover is set).
    public record PromotionView(
            UUID id,
            String title,
            String shortDescription,
            String longDescription,
            String serviceCategory,
            String ctaText,
            PromotionAction ctaAction,
            String coverUrl,
            int displayOrder) {}

    /// `GET /promotions/manage` and the physio mutating responses — adds the management
    /// fields (visibility, schedule window, audit timestamps) on top of the patient view.
    public record ManageView(
            UUID id,
            String title,
            String shortDescription,
            String longDescription,
            String serviceCategory,
            String ctaText,
            PromotionAction ctaAction,
            String coverUrl,
            int displayOrder,
            boolean active,
            Instant startsAt,
            Instant endsAt,
            Instant createdAt,
            Instant updatedAt) {}

    public record PromotionListResponse(List<PromotionView> promotions) {}

    public record ManageListResponse(List<ManageView> promotions) {}

    /// `POST /promotions`. [title] required; everything else optional. [active] defaults
    /// to true when null.
    public record CreateRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 280) String shortDescription,
            @Size(max = 8000) String longDescription,
            @Size(max = 80) String serviceCategory,
            @Size(max = 40) String ctaText,
            PromotionAction ctaAction,
            Instant startsAt,
            Instant endsAt,
            Boolean active) {}

    /// `PATCH /promotions/{id}`. Replace semantics — the body fully describes the desired
    /// content/schedule state. Visibility is changed via the activate endpoint.
    public record UpdateRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 280) String shortDescription,
            @Size(max = 8000) String longDescription,
            @Size(max = 80) String serviceCategory,
            @Size(max = 40) String ctaText,
            PromotionAction ctaAction,
            Instant startsAt,
            Instant endsAt) {}

    public record SetActiveRequest(@NotNull Boolean active) {}

    public record ReorderRequest(@NotEmpty List<@NotNull UUID> orderedIds) {}

    public record CoverPresignRequest(
            @NotBlank String mimeType,
            @Positive long sizeBytes) {}

    public record CoverPresignView(
            String objectKey,
            String url,
            String contentType,
            long expiresInSeconds) {}

    public record CoverConfirmRequest(
            @NotBlank String objectKey,
            @NotBlank String mimeType) {}
}

package com.healyn.promotions.service;

import com.healyn.promotions.domain.PromotionAction;

import java.time.Instant;

/// Command to create a promotion. Text fields are trimmed/blank-to-null by the service;
/// [action] defaults to NONE when null; [active] defaults to true when null. The cover
/// image is attached separately via the presign/confirm endpoints.
public record NewPromotion(
        String title,
        String shortDescription,
        String longDescription,
        String serviceCategory,
        String ctaText,
        PromotionAction ctaAction,
        Instant startsAt,
        Instant endsAt,
        Boolean active) {
}

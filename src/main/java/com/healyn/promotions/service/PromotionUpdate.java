package com.healyn.promotions.service;

import com.healyn.promotions.domain.PromotionAction;

import java.time.Instant;

/// Command to edit a promotion's content and schedule. Replace semantics: the body fully
/// describes the desired state (the single in-app editor always sends every field), so a
/// blank/null optional field clears it. [title] is required; [ctaAction] null means NONE.
/// Visibility is changed via the dedicated activate endpoint, not here.
public record PromotionUpdate(
        String title,
        String shortDescription,
        String longDescription,
        String serviceCategory,
        String ctaText,
        PromotionAction ctaAction,
        Instant startsAt,
        Instant endsAt) {
}

package com.healyn.promotions.web;

import com.healyn.promotions.domain.Promotion;

final class PromotionMapper {

    private PromotionMapper() {}

    /// Patient-facing view: only fields safe to show a patient. [coverUrl] is a freshly
    /// presigned GET URL (or null).
    static PromotionDtos.PromotionView toPatientView(Promotion p, String coverUrl) {
        return new PromotionDtos.PromotionView(
                p.getId(),
                p.getTitle(),
                p.getShortDescription(),
                p.getLongDescription(),
                p.getServiceCategory(),
                p.getCtaText(),
                p.getCtaAction(),
                coverUrl,
                p.getDisplayOrder());
    }

    /// Physio management view: adds visibility, schedule window, and audit timestamps.
    static PromotionDtos.ManageView toManageView(Promotion p, String coverUrl) {
        return new PromotionDtos.ManageView(
                p.getId(),
                p.getTitle(),
                p.getShortDescription(),
                p.getLongDescription(),
                p.getServiceCategory(),
                p.getCtaText(),
                p.getCtaAction(),
                coverUrl,
                p.getDisplayOrder(),
                p.isActive(),
                p.getStartsAt(),
                p.getEndsAt(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}

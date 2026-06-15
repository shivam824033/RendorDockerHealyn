package com.healyn.promotions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// Tunables for the clinic-promotions surface (FEATURE_ROADMAP F1.23). {@code maxActive}
/// is the configurable cap on simultaneously-active promotions — it bounds the patient
/// carousel so Home stays calm. Override with {@code HEALYN_PROMOTIONS_MAX_ACTIVE}.
@ConfigurationProperties(prefix = "healyn.promotions")
public record PromotionProperties(Integer maxActive) {

    public PromotionProperties {
        if (maxActive == null || maxActive < 1) {
            maxActive = 12;
        }
    }
}

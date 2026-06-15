package com.healyn.compliance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// Tuning for the compliance surface. Bound from {@code healyn.compliance.*}.
///
/// <p>{@code graceDays} is the cancellable window between a deletion request and
/// anonymization. {@code purgeEnabled} guards the hard-purge of de-identified clinical
/// scaffolding after {@code retentionDays}; it defaults OFF because purging the clinical
/// tables collides with Hard Rule #7 and needs explicit human sign-off (audit §11).
@ConfigurationProperties(prefix = "healyn.compliance")
public record ComplianceProperties(
        @DefaultValue("true") boolean pollerEnabled,
        @DefaultValue("60000") long pollIntervalMs,
        @DefaultValue("30") int graceDays,
        @DefaultValue("en") String defaultLocale,
        @DefaultValue("false") boolean purgeEnabled,
        @DefaultValue("2920") int retentionDays) {}

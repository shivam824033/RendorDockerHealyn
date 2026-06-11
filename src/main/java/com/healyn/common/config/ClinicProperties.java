package com.healyn.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/// Single-clinic settings (Phase 1). The clinic timezone is the reference frame for
/// clinic-local calendar days — e.g. the YYYYMMDD stem of a human-friendly Appointment
/// Number (PHY-YYYYMMDD-NNNN). Instants are still stored as UTC everywhere; only the
/// display-date derivation uses this zone. Override with HEALYN_CLINIC_TIMEZONE.
@ConfigurationProperties(prefix = "healyn.clinic")
public record ClinicProperties(String timezone) {

    public ClinicProperties {
        if (timezone == null || timezone.isBlank()) {
            timezone = "Asia/Kolkata";
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}

package com.healyn.auth.web;

import com.healyn.auth.service.DeviceMeta;
import jakarta.servlet.http.HttpServletRequest;

final class HttpClientInfo {

    private HttpClientInfo() {}

    static DeviceMeta enrich(AuthDtos.DeviceRequest req, HttpServletRequest http) {
        return new DeviceMeta(
                req.deviceId(),
                req.deviceLabel(),
                req.fcmToken(),
                clientIp(http),
                truncate(http.getHeader("User-Agent"), 512));
    }

    static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return http.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

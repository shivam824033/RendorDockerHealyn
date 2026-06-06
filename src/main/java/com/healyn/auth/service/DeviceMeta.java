package com.healyn.auth.service;

public record DeviceMeta(String deviceId, String deviceLabel, String fcmToken,
                         String ipAddress, String userAgent) {
}

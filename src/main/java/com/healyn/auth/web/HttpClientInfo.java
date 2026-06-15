package com.healyn.auth.web;

import com.healyn.auth.service.DeviceMeta;
import com.healyn.common.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;

final class HttpClientInfo {

    private HttpClientInfo() {}

    static DeviceMeta enrich(AuthDtos.DeviceRequest req, HttpServletRequest http) {
        return new DeviceMeta(
                req.deviceId(),
                req.deviceLabel(),
                req.fcmToken(),
                ClientInfo.clientIp(http),
                ClientInfo.userAgent(http));
    }

    static String clientIp(HttpServletRequest http) {
        return ClientInfo.clientIp(http);
    }
}

package com.healyn.common.web;

import jakarta.servlet.http.HttpServletRequest;

/// Extracts client network metadata (IP, user agent) from a request. Shared by modules that
/// persist this metadata on consent/session records and by the auth rate limiter.
///
/// The client IP is the framework-resolved {@code getRemoteAddr()} — NOT a raw client-supplied
/// header (audit M2). With {@code server.forward-headers-strategy=framework} Spring's
/// ForwardedHeaderFilter already folds the trusted reverse-proxy's forwarding header into
/// {@code getRemoteAddr()}; parsing the leftmost {@code X-Forwarded-For} value here would instead
/// trust the most client-controllable (spoofable) hop. The edge gateway MUST overwrite, not
/// append, forwarding headers so a client cannot inject a fake origin IP.
public final class ClientInfo {

    private static final int USER_AGENT_MAX = 512;

    private ClientInfo() {}

    public static String clientIp(HttpServletRequest http) {
        return http.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest http) {
        return truncate(http.getHeader("User-Agent"), USER_AGENT_MAX);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

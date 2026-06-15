package com.healyn.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healyn.auth.config.AuthProperties;
import com.healyn.auth.service.RateLimiter;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.logging.TraceContext;
import com.healyn.common.web.ApiError;
import com.healyn.common.web.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Duration;

/// Per-IP abuse protection for the unauthenticated auth endpoints (audit H1/H2/M4/M5). Runs
/// ahead of the controllers and rejects, before any password hashing or OTP work:
///   - requests whose body exceeds {@code healyn.ratelimit.max-body-bytes} (413), and
///   - clients that exceed the per-IP fixed-window budget for the endpoint class (429).
/// The client IP is the framework-resolved {@code getRemoteAddr()} (see ClientInfo / the
/// {@code forward-headers-strategy} gateway contract), not a raw client-supplied header.
/// This complements — does not replace — the per-account lockout and per-target OTP cap.
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private final RateLimiter limiter;
    private final AuthProperties.RateLimit props;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(RateLimiter limiter, AuthProperties.RateLimit props, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.enabled()) {
            return true;
        }
        return !HttpMethod.POST.matches(request.getMethod()) || !path(request).startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > props.maxBodyBytes()) {
            write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    ErrorCode.VALIDATION_FAILED, "Request body is too large.");
            return;
        }

        AuthProperties.RateLimit.Rule rule = ruleFor(path(request));
        if (rule != null) {
            String ip = request.getRemoteAddr();
            if (!limiter.tryAcquire(bucket(path(request)), ip, rule.maxRequests(),
                    Duration.ofSeconds(rule.windowSeconds()))) {
                log.warn("Auth rate limit exceeded on {} from {}", path(request), ip);
                write(response, 429, ErrorCode.RATE_LIMITED, "Too many requests. Try again later.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private AuthProperties.RateLimit.Rule ruleFor(String path) {
        return switch (path) {
            case "/auth/login" -> props.login();
            case "/auth/refresh" -> props.refresh();
            case "/auth/register/start", "/auth/password-reset/start" -> props.otpStart();
            default -> null;
        };
    }

    private static String bucket(String path) {
        return switch (path) {
            case "/auth/login" -> "login";
            case "/auth/refresh" -> "refresh";
            default -> "otp_start";
        };
    }

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    /// Path within the application (context path stripped). Uses UrlPathHelper rather than
    /// getServletPath() so matching is correct behind a context path — and under MockMvc, which
    /// populates the request URI but not the servlet path.
    private static String path(HttpServletRequest request) {
        return PATH_HELPER.getPathWithinApplication(request);
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        ApiError err = ApiError.of(code, message, TraceContext.currentTraceId());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(err));
    }
}

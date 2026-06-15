package com.healyn.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healyn.auth.config.AuthProperties;
import com.healyn.auth.service.JwtBlacklist;
import com.healyn.auth.service.JwtKeyProvider;
import com.healyn.auth.service.RateLimiter;
import com.healyn.auth.web.AuthRateLimitFilter;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.logging.TraceContext;
import com.healyn.common.web.ApiError;
import com.healyn.common.web.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
public class SecurityConfig {

    private static final String[] OPENAPI_WHITELIST = {
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/**",
            "/swagger-ui",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**"
    };

    private final ObjectMapper objectMapper;
    private final Environment environment;

    public SecurityConfig(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RateLimiter rateLimiter,
                                                   AuthProperties.RateLimit rateLimitProps) throws Exception {
        AuthRateLimitFilter rateLimitFilter = new AuthRateLimitFilter(rateLimiter, rateLimitProps, objectMapper);
        // OpenAPI/Swagger is dev-only. In prod springdoc is disabled (application-prod.yml)
        // AND the paths are not anonymously whitelisted, so the spec/UI can never be reached
        // by an unauthenticated client (audit S-2).
        boolean openApiExposed = !environment.acceptsProfiles(Profiles.of("prod"));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Security headers (audit M3). HSTS pins TLS for a year incl. subdomains; the API
                // serves only JSON to a native client, so deny framing and lock down refer/CTO.
                // Spring's defaults already add X-Content-Type-Options and Cache-Control no-store.
                .headers(h -> h
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .frameOptions(org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig::deny)
                        .referrerPolicy(rp -> rp.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .addFilterBefore(rateLimitFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(reg -> {
                    reg.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
                    if (openApiExposed) {
                        reg.requestMatchers(OPENAPI_WHITELIST).permitAll();
                    }
                    reg.requestMatchers(
                                    "/auth/register/**",
                                    "/auth/login",
                                    "/auth/refresh",
                                    "/auth/password-reset/**").permitAll()
                            // Legal documents (Privacy Policy / Terms) must be readable before
                            // signup and during app-store review — read-only, no PHI.
                            .requestMatchers(HttpMethod.GET, "/legal/**").permitAll()
                            .anyRequest().authenticated();
                })
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Authentication required."))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "Access denied.")));
        return http.build();
    }

    @Bean
    public NimbusJwtDecoder jwtDecoder(JwtKeyProvider keys, AuthProperties.Jwt props, JwtBlacklist blacklist) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> chain = new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                List.of(
                        new JwtTimestampValidator(),
                        new JwtIssuerValidator(props.issuer()),
                        new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                                aud -> aud != null && aud.contains(props.audience())),
                        notRevoked(blacklist)
                ));
        decoder.setJwtValidator(chain);
        return decoder;
    }

    private static OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> notRevoked(JwtBlacklist blacklist) {
        OAuth2Error revoked = new OAuth2Error("token_revoked", "Token has been revoked", null);
        return jwt -> {
            // Reject a single revoked token (jti) or one whose device session has been
            // signed out / revoked (sid) — the latter invalidates a device immediately,
            // not just at the access token's natural expiry.
            String sid = jwt.getClaimAsString("sid");
            boolean blocked = blacklist.isRevoked(jwt.getId())
                    || (sid != null && blacklist.isSessionRevoked(sid));
            return blocked ? OAuth2TokenValidatorResult.failure(revoked) : OAuth2TokenValidatorResult.success();
        };
    }

    private static JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority(role));
        });
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        ApiError err = ApiError.of(code, message, TraceContext.currentTraceId());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(err));
    }
}

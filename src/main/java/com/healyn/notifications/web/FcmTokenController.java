package com.healyn.notifications.web;

import com.healyn.notifications.service.FcmTokenService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Device push-token registration. Path lives under {@code /auth} per API_STANDARDS §9.1,
 * but the resource is owned by the notifications module, so the controller sits here to
 * keep {@code auth} free of a dependency on {@code notifications}.
 */
@RestController
@RequestMapping("/auth/fcm_tokens")
public class FcmTokenController {

    private final FcmTokenService tokens;

    public FcmTokenController(FcmTokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping
    public FcmTokenDtos.RegisterResponse register(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody FcmTokenDtos.RegisterRequest body) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        UUID id = tokens.register(accountId, body.token(), body.platform(), body.deviceId());
        return new FcmTokenDtos.RegisterResponse(id);
    }
}

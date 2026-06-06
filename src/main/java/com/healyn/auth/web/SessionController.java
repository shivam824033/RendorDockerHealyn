package com.healyn.auth.web;

import com.healyn.auth.service.DeviceSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/auth/sessions")
public class SessionController {

    private final DeviceSessionService sessions;

    public SessionController(DeviceSessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    public AuthDtos.SessionListResponse list(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        List<AuthDtos.SessionView> views = sessions.listActive(accountId).stream()
                .map(s -> new AuthDtos.SessionView(
                        s.getId(), s.getDeviceId(), s.getDeviceLabel(),
                        s.getIssuedAt(), s.getLastSeenAt(), s.getExpiresAt()))
                .toList();
        return new AuthDtos.SessionListResponse(views);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        sessions.revoke(id, UUID.fromString(jwt.getSubject()));
    }
}

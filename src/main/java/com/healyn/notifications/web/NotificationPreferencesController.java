package com.healyn.notifications.web;

import com.healyn.notifications.domain.NotificationPreferences;
import com.healyn.notifications.service.NotificationPreferencesService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Push-notification opt-outs for the authenticated account (API_STANDARDS §9.8). Preferences are
 * account-scoped, so the subject claim is the only identity needed — no access policy applies.
 */
@RestController
@RequestMapping("/notifications/preferences")
public class NotificationPreferencesController {

    private final NotificationPreferencesService preferences;

    public NotificationPreferencesController(NotificationPreferencesService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public NotificationPreferenceDtos.PreferencesView get(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        return NotificationPreferenceDtos.PreferencesView.from(preferences.get(accountId));
    }

    @PatchMapping
    public NotificationPreferenceDtos.PreferencesView update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NotificationPreferenceDtos.UpdateRequest body) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        NotificationPreferences updated = preferences.update(accountId,
                body.appointmentUpdates(), body.appointmentReminders(),
                body.messages(), body.treatmentNotes());
        return NotificationPreferenceDtos.PreferencesView.from(updated);
    }
}

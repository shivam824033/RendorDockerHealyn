package com.healyn.notifications.service;

import com.healyn.notifications.domain.NotificationCategory;
import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.domain.NotificationPreferences;
import com.healyn.notifications.repository.NotificationPreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reads and updates an account's push-notification opt-outs (API_STANDARDS §9.8). The default
 * is opted-in to everything, so a missing row is treated as all-enabled and never written by a
 * read; a row is created lazily the first time the account changes something.
 *
 * <p>{@link #isEnabledFor} is the gate {@link NotificationPublisher} consults at enqueue time —
 * it carries no transaction of its own so it joins the caller's, keeping the check inside the
 * same transaction as the action that triggered the notification.
 */
@Service
public class NotificationPreferencesService {

    private final NotificationPreferencesRepository preferences;

    public NotificationPreferencesService(NotificationPreferencesRepository preferences) {
        this.preferences = preferences;
    }

    /** Current preferences, or transient all-enabled defaults if the account has none stored. */
    @Transactional(readOnly = true)
    public NotificationPreferences get(UUID accountId) {
        return preferences.findById(accountId)
                .orElseGet(() -> NotificationPreferences.defaultsFor(accountId));
    }

    /** Apply a partial update, creating the row on first change; null fields are left unchanged. */
    @Transactional
    public NotificationPreferences update(UUID accountId, Boolean appointmentUpdates,
                                          Boolean appointmentReminders, Boolean messages,
                                          Boolean treatmentNotes) {
        NotificationPreferences current = preferences.findById(accountId)
                .orElseGet(() -> NotificationPreferences.defaultsFor(accountId));
        current.apply(appointmentUpdates, appointmentReminders, messages, treatmentNotes);
        return preferences.save(current);
    }

    /** Whether the account wants push for this kind's category; defaults to true when unset. */
    public boolean isEnabledFor(UUID accountId, NotificationKind kind) {
        NotificationCategory category = NotificationCategory.of(kind);
        return preferences.findById(accountId)
                .map(p -> p.enabledFor(category))
                .orElse(true);
    }
}

package com.healyn.notifications.service;

import com.healyn.common.id.UuidV7;
import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.domain.NotificationOutbox;
import com.healyn.notifications.repository.NotificationOutboxRepository;
import com.healyn.patients.repository.AccountPatientRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enqueues outbound notifications. Called from within a domain service's transaction
 * so the outbox row commits atomically with the action that produced it. Payloads
 * carry IDs only — never PHI (CLAUDE.md Hard Rule #4). Dispatch is handled separately
 * by the outbox poller.
 *
 * <p>A recipient who has opted out of the kind's category (API_STANDARDS §9.8) is skipped
 * here, at enqueue: no row is written, so an opt-out never even reaches the outbox.
 */
@Service
public class NotificationPublisher {

    private final NotificationOutboxRepository outbox;
    private final AccountPatientRepository accountPatients;
    private final NotificationPreferencesService preferences;
    private final Clock clock;

    public NotificationPublisher(NotificationOutboxRepository outbox,
                                 AccountPatientRepository accountPatients,
                                 NotificationPreferencesService preferences,
                                 Clock clock) {
        this.outbox = outbox;
        this.accountPatients = accountPatients;
        this.preferences = preferences;
        this.clock = clock;
    }

    /** Enqueue one notification to a single account, unless it has opted out of this category. */
    public void enqueueToAccount(NotificationKind kind, UUID targetAccountId,
                                 Map<String, String> payload, UUID correlationId) {
        if (!preferences.isEnabledFor(targetAccountId, kind)) {
            return;
        }
        outbox.save(new NotificationOutbox(
                UuidV7.generate(), kind, targetAccountId, payload, correlationId, Instant.now(clock)));
    }

    /**
     * Fan-out: enqueue one notification per account that can manage the patient and has not
     * opted out of this category. Returns the number of rows actually written.
     */
    public int enqueueToPatientManagers(NotificationKind kind, UUID patientId,
                                        Map<String, String> payload, UUID correlationId) {
        List<UUID> managers = accountPatients.findManagerAccountIds(patientId);
        Instant now = Instant.now(clock);
        int written = 0;
        for (UUID accountId : managers) {
            if (!preferences.isEnabledFor(accountId, kind)) {
                continue;
            }
            outbox.save(new NotificationOutbox(
                    UuidV7.generate(), kind, accountId, payload, correlationId, now));
            written++;
        }
        return written;
    }
}

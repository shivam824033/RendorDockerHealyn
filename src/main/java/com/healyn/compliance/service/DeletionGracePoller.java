package com.healyn.compliance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// Sweeps for deletion requests whose grace window has elapsed and anonymizes them. Disabled
/// via {@code healyn.compliance.poller-enabled=false} (e.g. in tests, which call
/// {@link AccountDeletionService#processDueAnonymizations()} directly). One sweep's failure
/// must never stop the schedule, so exceptions are swallowed and logged (mirrors the outbox poller).
@Component
@ConditionalOnProperty(prefix = "healyn.compliance", name = "poller-enabled",
        havingValue = "true", matchIfMissing = true)
public class DeletionGracePoller {

    private static final Logger log = LoggerFactory.getLogger(DeletionGracePoller.class);

    private final AccountDeletionService deletions;

    public DeletionGracePoller(AccountDeletionService deletions) {
        this.deletions = deletions;
    }

    @Scheduled(fixedDelayString = "${healyn.compliance.poll-interval-ms:60000}")
    public void poll() {
        try {
            deletions.processDueAnonymizations();
        } catch (RuntimeException e) {
            log.warn("deletion grace sweep failed", e);
        }
    }
}

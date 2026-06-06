package com.healyn.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the outbox dispatch sweep on a fixed delay. Disabled via
 * {@code healyn.notifications.poller-enabled=false} (e.g. in tests, which invoke
 * {@link OutboxDispatcher#dispatchDue()} directly). One sweep's failure must never
 * stop the schedule, so exceptions are swallowed and logged.
 */
@Component
@ConditionalOnProperty(prefix = "healyn.notifications", name = "poller-enabled",
        havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxDispatcher dispatcher;

    public OutboxPoller(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${healyn.notifications.poll-interval-ms:2000}")
    public void poll() {
        try {
            dispatcher.dispatchDue();
        } catch (RuntimeException e) {
            log.warn("outbox dispatch sweep failed", e);
        }
    }
}

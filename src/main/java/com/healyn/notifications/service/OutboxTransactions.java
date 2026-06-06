package com.healyn.notifications.service;

import com.healyn.notifications.config.NotificationProperties;
import com.healyn.notifications.domain.FcmToken;
import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.domain.NotificationOutbox;
import com.healyn.notifications.domain.NotificationStatus;
import com.healyn.notifications.repository.FcmTokenRepository;
import com.healyn.notifications.repository.NotificationOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two short database transactions that bracket one outbox dispatch, kept separate from
 * {@link OutboxDispatcher} so the FCM network call happens <em>between</em> them and never
 * inside a transaction (no row lock or pooled connection is held across external I/O):
 *
 * <ol>
 *   <li>{@link #claimDue} — lock due rows {@code SKIP LOCKED}, lease them so a concurrent
 *       instance skips them, commit.</li>
 *   <li><em>(dispatcher sends to FCM with no transaction open)</em></li>
 *   <li>{@link #recordOutcome} — re-load the row in its own transaction and finalise it,
 *       retiring any tokens FCM rejected.</li>
 * </ol>
 *
 * <p>These live in a separate bean on purpose: a self-invoked {@code @Transactional} method
 * would bypass the proxy and run with no transaction at all.
 */
@Service
public class OutboxTransactions {

    /** A row reserved for dispatch. Carries only the basics the send step needs. */
    public record Claim(UUID id, UUID accountId, NotificationKind kind, Map<String, String> payload) {}

    /** Result of attempting delivery for one claim, applied by {@link #recordOutcome}. */
    public record SendResult(String deliveredToken, boolean transientFailure,
                             String lastError, List<String> invalidTokens) {

        static SendResult delivered(String token) {
            return new SendResult(token, false, null, List.of());
        }

        static SendResult terminal(List<String> invalidTokens) {
            return new SendResult(null, false, null, invalidTokens);
        }

        static SendResult transient_(String error, List<String> invalidTokens) {
            return new SendResult(null, true, error, invalidTokens);
        }
    }

    private final NotificationOutboxRepository outbox;
    private final FcmTokenRepository tokens;
    private final NotificationProperties props;

    public OutboxTransactions(NotificationOutboxRepository outbox,
                              FcmTokenRepository tokens,
                              NotificationProperties props) {
        this.outbox = outbox;
        this.tokens = tokens;
        this.props = props;
    }

    /** Claim a batch of due rows and lease them; commits before any delivery is attempted. */
    @Transactional
    public List<Claim> claimDue(Instant now) {
        List<NotificationOutbox> due = outbox.findDueForDispatch(
                NotificationStatus.PENDING, now, PageRequest.of(0, props.batchSize()));
        Instant leaseUntil = now.plusSeconds(props.leaseSeconds());
        List<Claim> claims = new ArrayList<>(due.size());
        for (NotificationOutbox row : due) {
            row.leaseUntil(leaseUntil);
            claims.add(new Claim(row.getId(), row.getTargetAccountId(), row.getKind(), row.getPayload()));
        }
        return claims;
    }

    /** Apply the delivery result to one row in its own transaction. */
    @Transactional
    public void recordOutcome(UUID rowId, Instant now, SendResult result) {
        NotificationOutbox row = outbox.findById(rowId).orElse(null);
        if (row == null || row.getStatus() != NotificationStatus.PENDING) {
            return; // already finalised by another path, or gone
        }
        for (String invalidToken : result.invalidTokens()) {
            tokens.findByTokenAndDeletedAtIsNull(invalidToken).ifPresent(FcmToken::retire);
        }
        if (result.deliveredToken() != null) {
            row.markSent(now, result.deliveredToken());
        } else if (result.transientFailure()) {
            int attemptNo = row.getAttempts() + 1;
            if (attemptNo >= props.maxAttempts()) {
                row.markDead(result.lastError());
            } else {
                row.reschedule(now.plusSeconds(backoffSeconds(attemptNo)), result.lastError());
            }
        } else {
            // Tokens existed but every one was invalid (now retired), or there were none: terminal.
            row.markSent(now, null);
        }
    }

    private long backoffSeconds(int attemptNo) {
        return props.backoffBaseSeconds() * (1L << (attemptNo - 1));
    }
}

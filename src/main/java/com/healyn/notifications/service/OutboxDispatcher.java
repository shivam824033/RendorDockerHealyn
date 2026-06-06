package com.healyn.notifications.service;

import com.healyn.notifications.domain.FcmToken;
import com.healyn.notifications.domain.NotificationKind;
import com.healyn.notifications.port.FcmSendOutcome;
import com.healyn.notifications.port.FcmSenderPort;
import com.healyn.notifications.repository.FcmTokenRepository;
import com.healyn.notifications.service.OutboxTransactions.Claim;
import com.healyn.notifications.service.OutboxTransactions.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drains due {@code notification_outbox} rows. Each sweep claims a batch in a short transaction
 * (rows leased so other instances skip them), then for each claimed row pushes to the recipient's
 * live FCM tokens <em>with no transaction open</em>, and finally records the outcome in a second
 * short transaction. Keeping the FCM call outside any transaction means no row lock or pooled
 * connection is held across external I/O, and a row that fails to send is isolated: it never
 * rolls back or blocks the rest of the batch.
 *
 * <p>The poller calls {@link #dispatchDue()} on a fixed delay; tests call it directly. The
 * transaction boundaries live in {@link OutboxTransactions}.
 */
@Service
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxTransactions transactions;
    private final FcmTokenRepository tokens;
    private final FcmSenderPort sender;
    private final Clock clock;

    public OutboxDispatcher(OutboxTransactions transactions,
                            FcmTokenRepository tokens,
                            FcmSenderPort sender,
                            Clock clock) {
        this.transactions = transactions;
        this.tokens = tokens;
        this.sender = sender;
        this.clock = clock;
    }

    /** Claim, deliver, and finalise one batch of due rows. Returns the number of rows claimed. */
    public int dispatchDue() {
        Instant now = Instant.now(clock);
        List<Claim> claimed = transactions.claimDue(now);
        for (Claim claim : claimed) {
            try {
                SendResult result = trySend(claim);
                transactions.recordOutcome(claim.id(), now, result);
            } catch (RuntimeException e) {
                // Isolate a poisoned row: never let it abort the rest of the batch. It stays
                // leased and becomes due again after the lease window for a later sweep.
                log.warn("failed to dispatch outbox row {}", claim.id(), e);
            }
        }
        return claimed.size();
    }

    /**
     * Push one claim to every live token for its account. Runs outside any transaction; reads
     * tokens, then sends. A send that throws is treated as a transient failure for that token so
     * the row is rescheduled (and eventually retired) rather than wedging the queue.
     */
    private SendResult trySend(Claim claim) {
        List<FcmToken> liveTokens = tokens.findByAccountIdAndDeletedAtIsNull(claim.accountId());
        if (liveTokens.isEmpty()) {
            return SendResult.terminal(List.of()); // nothing to deliver to
        }

        String delivered = null;
        boolean transientFailure = false;
        String lastError = null;
        List<String> invalid = new ArrayList<>();
        for (FcmToken token : liveTokens) {
            FcmSendOutcome outcome = send(claim.id(), token.getToken(), claim.kind(), claim.payload());
            switch (outcome) {
                case DELIVERED -> delivered = token.getToken();
                case TOKEN_INVALID -> invalid.add(token.getToken());
                case TRANSIENT_ERROR -> {
                    transientFailure = true;
                    lastError = "transient_fcm_error";
                }
            }
        }

        if (delivered != null) {
            return SendResult.delivered(delivered);
        }
        if (transientFailure) {
            return SendResult.transient_(lastError, invalid);
        }
        // Every token was invalid (to be retired): nothing deliverable, nothing to retry.
        return SendResult.terminal(invalid);
    }

    private FcmSendOutcome send(UUID rowId, String token, NotificationKind kind, Map<String, String> payload) {
        try {
            return sender.send(token, kind, payload);
        } catch (RuntimeException e) {
            log.warn("FCM send threw for outbox row {}", rowId, e);
            return FcmSendOutcome.TRANSIENT_ERROR;
        }
    }
}

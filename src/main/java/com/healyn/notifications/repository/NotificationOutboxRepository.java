package com.healyn.notifications.repository;

import com.healyn.notifications.domain.NotificationOutbox;
import com.healyn.notifications.domain.NotificationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    List<NotificationOutbox> findByCorrelationIdOrderByCreatedAtAsc(UUID correlationId);

    /**
     * Due rows for one dispatch sweep, row-locked with SKIP LOCKED so concurrent pollers
     * (multiple app instances) never claim the same row. Hibernate maps lock timeout {@code -2}
     * to {@code SKIP LOCKED}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from NotificationOutbox o "
            + "where o.status = :status and o.nextAttemptAt <= :now "
            + "order by o.nextAttemptAt asc")
    List<NotificationOutbox> findDueForDispatch(@Param("status") NotificationStatus status,
                                                @Param("now") Instant now,
                                                Pageable pageable);
}

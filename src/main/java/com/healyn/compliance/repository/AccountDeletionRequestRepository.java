package com.healyn.compliance.repository;

import com.healyn.compliance.domain.AccountDeletionRequest;
import com.healyn.compliance.domain.DeletionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, UUID> {

    /// The account's active (still-cancellable) request, if any. The partial unique index
    /// {@code idx_account_deletion_active} guarantees at most one.
    Optional<AccountDeletionRequest> findByAccountIdAndStatus(UUID accountId, DeletionRequestStatus status);

    /// Requests whose grace window has elapsed and are awaiting anonymization.
    List<AccountDeletionRequest> findByStatusAndPurgeAfterLessThanEqual(DeletionRequestStatus status, Instant cutoff);
}

package com.healyn.compliance.repository;

import com.healyn.compliance.domain.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    /// The account's consent history, newest first — grants and withdrawals interleaved.
    List<ConsentRecord> findByAccountIdOrderByGrantedAtDesc(UUID accountId);
}

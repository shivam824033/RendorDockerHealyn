package com.healyn.auth.repository;

import com.healyn.auth.domain.DeviceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {

    Optional<DeviceSession> findByRefreshTokenHash(byte[] refreshTokenHash);

    List<DeviceSession> findAllByAccountIdAndRevokedAtIsNull(UUID accountId);

    @Modifying
    @Query("update DeviceSession d set d.revokedAt = :when where d.accountId = :accountId and d.revokedAt is null")
    int revokeAllForAccount(@Param("accountId") UUID accountId, @Param("when") Instant when);
}

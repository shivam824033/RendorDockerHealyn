package com.healyn.auth.repository;

import com.healyn.auth.domain.DeviceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {

    Optional<DeviceSession> findByRefreshTokenHash(byte[] refreshTokenHash);

    List<DeviceSession> findAllByAccountIdAndRevokedAtIsNull(UUID accountId);

    /// Erasure: drop every session row for an account — they carry device label, IP and
    /// user-agent. Sessions are revoked at request time; this removes the residue at anonymization.
    @Modifying
    @Query("delete from DeviceSession d where d.accountId = :accountId")
    int deleteByAccountId(@Param("accountId") UUID accountId);
}

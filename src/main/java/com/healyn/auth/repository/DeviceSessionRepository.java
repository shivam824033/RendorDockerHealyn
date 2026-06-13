package com.healyn.auth.repository;

import com.healyn.auth.domain.DeviceSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {

    Optional<DeviceSession> findByRefreshTokenHash(byte[] refreshTokenHash);

    List<DeviceSession> findAllByAccountIdAndRevokedAtIsNull(UUID accountId);
}

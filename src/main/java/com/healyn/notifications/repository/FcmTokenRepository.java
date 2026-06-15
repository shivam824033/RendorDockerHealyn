package com.healyn.notifications.repository;

import com.healyn.notifications.domain.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

    Optional<FcmToken> findByTokenAndDeletedAtIsNull(String token);

    List<FcmToken> findByAccountIdAndDeletedAtIsNull(UUID accountId);

    List<FcmToken> findByAccountIdAndDeviceIdAndDeletedAtIsNull(UUID accountId, String deviceId);

    /// Erasure: hard-delete every token row for an account — the token string itself is a
    /// device identifier, so retiring (soft-delete) would leave it in the row.
    @Modifying
    @Query("delete from FcmToken t where t.accountId = :accountId")
    int deleteByAccountId(@Param("accountId") UUID accountId);
}

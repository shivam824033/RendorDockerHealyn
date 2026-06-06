package com.healyn.notifications.repository;

import com.healyn.notifications.domain.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

    Optional<FcmToken> findByTokenAndDeletedAtIsNull(String token);

    List<FcmToken> findByAccountIdAndDeletedAtIsNull(UUID accountId);
}

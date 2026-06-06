package com.healyn.notifications.repository;

import com.healyn.notifications.domain.NotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, UUID> {
}

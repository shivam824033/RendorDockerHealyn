package com.healyn.physio.repository;

import com.healyn.physio.domain.PhysioProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PhysioProfileRepository extends JpaRepository<PhysioProfile, UUID> {

    /// "The" profile. Single-tenant means there is exactly one row in production
    /// (PROJECT_CONTEXT §5.2); ordering by most-recently-updated keeps the patient
    /// read deterministic even where multiple physio accounts exist (e.g. tests).
    Optional<PhysioProfile> findTopByOrderByUpdatedAtDesc();
}

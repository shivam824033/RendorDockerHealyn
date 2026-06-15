package com.healyn.promotions.repository;

import com.healyn.promotions.domain.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    /// The patient read path: active, not-deleted, in-window promotions ordered by
    /// display priority then newest. Single-clinic — no clinic scoping in Phase 1.
    @Query("""
            select p from Promotion p
            where p.deletedAt is null
              and p.active = true
              and (p.startsAt is null or p.startsAt <= :now)
              and (p.endsAt is null or p.endsAt > :now)
            order by p.displayOrder asc, p.createdAt desc
            """)
    List<Promotion> findVisible(@Param("now") Instant now);

    /// Every non-deleted promotion for the physiotherapist's management view, in
    /// display order (active or not, in or out of window).
    List<Promotion> findByDeletedAtIsNullOrderByDisplayOrderAscCreatedAtDesc();

    Optional<Promotion> findByIdAndDeletedAtIsNull(UUID id);

    /// Count of currently-active, not-deleted promotions — enforces the configured cap.
    long countByActiveTrueAndDeletedAtIsNull();

    /// Highest display_order among non-deleted rows, for appending new content last.
    @Query("select max(p.displayOrder) from Promotion p where p.deletedAt is null")
    Integer findMaxDisplayOrder();
}

package com.healyn.availability.repository;

import com.healyn.availability.domain.AvailabilityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {

    @Query("""
            select r
            from AvailabilityRule r
            where r.physiotherapistId = :physioId
              and r.effectiveFrom <= :to
              and (r.effectiveTo is null or r.effectiveTo >= :from)
            order by r.dayOfWeek asc, r.startTime asc
            """)
    List<AvailabilityRule> findActiveByPhysio(
            @Param("physioId") UUID physioId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select r
            from AvailabilityRule r
            where r.physiotherapistId = :physioId
            order by r.effectiveTo asc nulls first, r.dayOfWeek asc, r.startTime asc
            """)
    List<AvailabilityRule> findByPhysio(@Param("physioId") UUID physioId);
}

package com.healyn.availability.repository;

import com.healyn.availability.domain.BlackoutWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BlackoutWindowRepository extends JpaRepository<BlackoutWindow, UUID> {

    @Query("""
            select b
            from BlackoutWindow b
            where b.physiotherapistId = :physioId
              and b.startsAt < :to
              and b.endsAt   > :from
            order by b.startsAt asc
            """)
    List<BlackoutWindow> findByPhysioOverlapping(
            @Param("physioId") UUID physioId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select b
            from BlackoutWindow b
            where b.physiotherapistId = :physioId
            order by b.startsAt asc
            """)
    List<BlackoutWindow> findByPhysio(@Param("physioId") UUID physioId);
}

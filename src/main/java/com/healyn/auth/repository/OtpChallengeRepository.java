package com.healyn.auth.repository;

import com.healyn.auth.domain.OtpChallenge;
import com.healyn.auth.domain.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    @Query("""
        select o from OtpChallenge o
        where o.target = :target and o.purpose = :purpose
          and o.consumedAt is null and o.expiresAt > :now
        order by o.createdAt desc
        """)
    Optional<OtpChallenge> findActive(@Param("target") String target,
                                      @Param("purpose") OtpPurpose purpose,
                                      @Param("now") Instant now);

    @Query("""
        select count(o) from OtpChallenge o
        where o.target = :target and o.createdAt > :since
        """)
    long countIssuedSince(@Param("target") String target, @Param("since") Instant since);
}

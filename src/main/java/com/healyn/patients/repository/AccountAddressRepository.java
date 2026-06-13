package com.healyn.patients.repository;

import com.healyn.patients.domain.AccountAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccountAddressRepository extends JpaRepository<AccountAddress, UUID> {

    /// The household address(es) reachable from a patient through its managing
    /// accounts, most-authoritative first (the account where the patient is that
    /// account's primary, then by account id for determinism). Callers take the
    /// first. A patient is normally managed by one account; the ordering only
    /// matters for the cross-account sharing edge case (PATIENT_RELATIONSHIP_MODEL §3.3).
    @Query("""
            select aa
            from AccountPatient ap, AccountAddress aa
            where ap.id.patientId = :patientId
              and aa.accountId = ap.id.accountId
            order by ap.primary desc, ap.id.accountId asc
            """)
    List<AccountAddress> findForPatient(@Param("patientId") UUID patientId);

    /// Batched form of {@link #findForPatient} for list endpoints — one query for a
    /// whole roster. Each row is {@code [patientId (UUID), address (AccountAddress)]},
    /// ordered so the preferred row per patient comes first; the caller reduces with
    /// put-if-absent.
    @Query("""
            select ap.id.patientId, aa
            from AccountPatient ap, AccountAddress aa
            where ap.id.patientId in :patientIds
              and aa.accountId = ap.id.accountId
            order by ap.primary desc, ap.id.accountId asc
            """)
    List<Object[]> findForPatients(@Param("patientIds") Collection<UUID> patientIds);
}

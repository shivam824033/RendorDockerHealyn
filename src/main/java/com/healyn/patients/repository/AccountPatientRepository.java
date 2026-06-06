package com.healyn.patients.repository;

import com.healyn.patients.domain.AccountPatient;
import com.healyn.patients.domain.AccountPatientId;
import com.healyn.patients.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountPatientRepository extends JpaRepository<AccountPatient, AccountPatientId> {

    @Query("""
            select p
            from AccountPatient ap, Patient p
            where ap.id.accountId = :accountId
              and ap.id.patientId = p.id
              and p.deletedAt is null
            order by ap.primary desc, p.fullName asc
            """)
    List<Patient> findActivePatientsForAccount(@Param("accountId") UUID accountId);

    @Query("""
            select ap
            from AccountPatient ap
            where ap.id.accountId = :accountId
              and ap.id.patientId = :patientId
            """)
    Optional<AccountPatient> findLink(@Param("accountId") UUID accountId, @Param("patientId") UUID patientId);

    @Query("select count(ap) from AccountPatient ap where ap.id.patientId = :patientId")
    long countLinksForPatient(@Param("patientId") UUID patientId);

    @Query("""
            select ap.id.accountId
            from AccountPatient ap
            where ap.id.patientId = :patientId
              and ap.canManage = true
            """)
    List<UUID> findManagerAccountIds(@Param("patientId") UUID patientId);

    @Query("""
            select case when count(ap) > 0 then true else false end
            from AccountPatient ap
            where ap.id.accountId = :accountId
              and ap.id.patientId = :patientId
              and ap.relationship = com.healyn.patients.domain.PatientRelationship.SELF
              and ap.primary = true
            """)
    boolean existsPrimaryLink(@Param("accountId") UUID accountId, @Param("patientId") UUID patientId);
}

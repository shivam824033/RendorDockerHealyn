package com.healyn.patients.repository;

import com.healyn.patients.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    // The physiotherapist's patient roster, newest-first, cursor-paginated. Optional search
    // matches the Patient ID as a prefix (case-insensitive: the term is upper-cased in the
    // service and patient_number is upper-case, so a case-sensitive LIKE hits the
    // text_pattern_ops index from V21) and the full name as a substring (ILIKE, served by the
    // V4 gin_trgm_ops index). Native because JPQL has no ILIKE and to keep the index operators
    // explicit. The default (unsearched) ordering walks idx_patients_created_id (V26).
    // :filterSearch toggles search off; when off the number/name params bind harmlessly.
    @Query(value = """
            select p.*
            from patients p
            where p.deleted_at is null
              and (:filterSearch = false
                   or p.patient_number like :numberPrefix
                   or p.full_name ilike :nameContains)
            order by p.created_at desc, p.id desc
            limit :limit
            """, nativeQuery = true)
    List<Patient> rosterFirstPage(
            @Param("filterSearch") boolean filterSearch,
            @Param("numberPrefix") String numberPrefix,
            @Param("nameContains") String nameContains,
            @Param("limit") int limit);

    // Continuation of rosterFirstPage past a (created_at, id) cursor. The keyset predicate
    // `created_at < pivot or (created_at = pivot and id < pivot)` matches the DESC ordering.
    @Query(value = """
            select p.*
            from patients p
            where p.deleted_at is null
              and (:filterSearch = false
                   or p.patient_number like :numberPrefix
                   or p.full_name ilike :nameContains)
              and (p.created_at < :pivotTime
                   or (p.created_at = :pivotTime and p.id < :pivotId))
            order by p.created_at desc, p.id desc
            limit :limit
            """, nativeQuery = true)
    List<Patient> rosterAfterCursor(
            @Param("filterSearch") boolean filterSearch,
            @Param("numberPrefix") String numberPrefix,
            @Param("nameContains") String nameContains,
            @Param("pivotTime") Instant pivotTime,
            @Param("pivotId") UUID pivotId,
            @Param("limit") int limit);
}

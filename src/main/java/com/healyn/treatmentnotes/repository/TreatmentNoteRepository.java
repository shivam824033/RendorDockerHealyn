package com.healyn.treatmentnotes.repository;

import com.healyn.treatmentnotes.domain.TreatmentNote;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentNoteRepository extends JpaRepository<TreatmentNote, UUID> {

    Optional<TreatmentNote> findByAppointmentIdAndDeletedAtIsNull(UUID appointmentId);

    @Query("""
            select n
            from TreatmentNote n
            where n.patientId = :patientId
              and n.deletedAt is null
            order by n.createdAt desc, n.id desc
            """)
    List<TreatmentNote> listFirstPage(
            @Param("patientId") UUID patientId,
            Limit limit);

    @Query("""
            select n
            from TreatmentNote n
            where n.patientId = :patientId
              and n.deletedAt is null
              and (n.createdAt < :pivotTime
                   or (n.createdAt = :pivotTime and n.id < :pivotId))
            order by n.createdAt desc, n.id desc
            """)
    List<TreatmentNote> listAfterCursor(
            @Param("patientId") UUID patientId,
            @Param("pivotTime") Instant pivotTime,
            @Param("pivotId") UUID pivotId,
            Limit limit);
}

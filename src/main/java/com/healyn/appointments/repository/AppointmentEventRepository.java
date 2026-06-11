package com.healyn.appointments.repository;

import com.healyn.appointments.domain.AppointmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AppointmentEventRepository extends JpaRepository<AppointmentEvent, Long> {

    /// The unified timeline of a whole lineage: events of every live appointment sharing the
    /// given root, oldest first (id breaks same-instant ties in insertion order, e.g. a
    /// reschedule's RESCHEDULED-then-CREATED pair). Events of soft-deleted lineage members are
    /// retained in the table but hidden here — visibility follows the appointments row.
    @Query("""
            select e
            from AppointmentEvent e
            where e.appointmentId in (
                select a.id
                from Appointment a
                where a.rootAppointmentId = :rootId
                  and a.deletedAt is null)
            order by e.occurredAt asc, e.id asc
            """)
    List<AppointmentEvent> findLineageTimeline(@Param("rootId") UUID rootId);
}

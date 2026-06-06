package com.healyn.discussion.repository;

import com.healyn.discussion.domain.DiscussionReadMarker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiscussionReadMarkerRepository
        extends JpaRepository<DiscussionReadMarker, DiscussionReadMarker.Key> {

    default Optional<DiscussionReadMarker> findFor(UUID appointmentId, UUID accountId) {
        return findById(new DiscussionReadMarker.Key(appointmentId, accountId));
    }
}

package com.healyn.files.repository;

import com.healyn.files.domain.FileObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FileObjectRepository extends JpaRepository<FileObject, UUID> {

    Optional<FileObject> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select count(f)
            from FileObject f
            where f.ownerAccountId = :ownerId
              and f.createdAt >= :since
            """)
    long countByOwnerSince(@Param("ownerId") UUID ownerId, @Param("since") Instant since);
}

package com.healyn.files.repository;

import com.healyn.auth.domain.AccountRole;
import com.healyn.files.domain.FileContext;
import com.healyn.files.domain.FileObject;
import com.healyn.files.domain.FileStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    // The enum filters are bound as parameters, not JPQL literals: a literal enum
    // emits a `'VALUE'::EnumClassName` cast Postgres can't resolve against the
    // snake_case named-enum types (`file_context`, `file_status`).
    @Query("""
            select f
            from FileObject f
            where f.patientId = :patientId
              and f.uploadContext = :context
              and f.status = :status
              and f.deletedAt is null
              and f.uploadedByRole = :uploadedByRole
            order by f.createdAt desc, f.id desc
            """)
    List<FileObject> listLibraryFirstPage(
            @Param("patientId") UUID patientId,
            @Param("uploadedByRole") AccountRole uploadedByRole,
            @Param("context") FileContext context,
            @Param("status") FileStatus status,
            Limit limit);

    @Query("""
            select f
            from FileObject f
            where f.patientId = :patientId
              and f.uploadContext = :context
              and f.status = :status
              and f.deletedAt is null
              and f.uploadedByRole = :uploadedByRole
              and (f.createdAt < :pivotTime
                   or (f.createdAt = :pivotTime and f.id < :pivotId))
            order by f.createdAt desc, f.id desc
            """)
    List<FileObject> listLibraryAfterCursor(
            @Param("patientId") UUID patientId,
            @Param("uploadedByRole") AccountRole uploadedByRole,
            @Param("context") FileContext context,
            @Param("status") FileStatus status,
            @Param("pivotTime") Instant pivotTime,
            @Param("pivotId") UUID pivotId,
            Limit limit);
}

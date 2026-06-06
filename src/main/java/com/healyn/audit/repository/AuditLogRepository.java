package com.healyn.audit.repository;

import com.healyn.audit.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    List<AuditLogEntry> findByResourceTypeAndResourceIdOrderByOccurredAtDesc(String resourceType, UUID resourceId);
}

package com.crmplatform.audit.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Every read is scoped explicitly: {@link #searchWorkspace} requires the caller's workspace id, and
 * {@link #searchPlatform} only returns platform-level rows (workspace NULL). There is deliberately
 * no unscoped finder besides the inherited ones, which are not used by the application.
 */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    @Query("""
            select a from AuditEntry a
            where a.workspaceId = :workspaceId
              and (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (:projectId is null or a.projectId = :projectId)
              and (:jobId is null or a.jobId = :jobId)
              and (:actorUserId is null or a.actorUserId = :actorUserId)
              and (cast(:from as timestamp) is null or a.occurredAt >= :from)
              and (cast(:to as timestamp) is null or a.occurredAt < :to)
            """)
    Page<AuditEntry> searchWorkspace(UUID workspaceId, String entityType, UUID entityId, UUID projectId, UUID jobId,
            UUID actorUserId, Instant from, Instant to, Pageable pageable);

    @Query("""
            select a from AuditEntry a
            where a.workspaceId is null
              and (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (cast(:from as timestamp) is null or a.occurredAt >= :from)
              and (cast(:to as timestamp) is null or a.occurredAt < :to)
            """)
    Page<AuditEntry> searchPlatform(String entityType, UUID entityId, Instant from, Instant to, Pageable pageable);
}

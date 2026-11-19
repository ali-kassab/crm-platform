package com.crmplatform.audit.application;

import com.crmplatform.audit.domain.AuditEntry;
import com.crmplatform.audit.domain.AuditEntryRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the audit log; keeps controllers away from repositories (layering rule). */
@Service
public class AuditQueries {

    private final AuditEntryRepository entries;

    public AuditQueries(AuditEntryRepository entries) {
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> workspace(UUID workspaceId, String entityType, UUID entityId, UUID projectId, UUID jobId,
            UUID actorUserId, Instant from, Instant to, Pageable pageable) {
        return entries.searchWorkspace(workspaceId, entityType, entityId, projectId, jobId, actorUserId, from, to,
                pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> platform(String entityType, UUID entityId, Instant from, Instant to, Pageable pageable) {
        return entries.searchPlatform(entityType, entityId, from, to, pageable);
    }
}

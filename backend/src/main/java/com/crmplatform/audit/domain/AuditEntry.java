package com.crmplatform.audit.domain;

import com.crmplatform.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * One append-only audit record (BR-111). Not a {@code TenantEntity}: platform-level entries have no
 * workspace, and the workspace is copied from the event that produced the entry. Reads are always
 * filtered explicitly by workspace in {@link AuditEntryRepository}; a database trigger rejects any
 * update or delete.
 */
@Entity
@Table(name = "audit_entries")
public class AuditEntry implements Persistable<UUID> {

    @Id
    private UUID id = UuidV7.generate();

    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_label", length = 120, updatable = false)
    private String actorLabel;

    @Column(nullable = false, length = 40, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "job_id", updatable = false)
    private UUID jobId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> changes;

    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Transient
    private boolean persisted;

    protected AuditEntry() {
    }

    public AuditEntry(Header header, String action, String entityType, UUID entityId, UUID projectId, UUID jobId,
            Map<String, Object> changes) {
        this.workspaceId = header.workspaceId();
        this.actorUserId = header.actorUserId();
        this.actorLabel = header.actorLabel();
        this.traceId = header.traceId();
        this.occurredAt = header.occurredAt();
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.projectId = projectId;
        this.jobId = jobId;
        this.changes = changes == null ? Map.of() : Map.copyOf(changes);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @jakarta.persistence.PostLoad
    @jakarta.persistence.PostPersist
    void markPersisted() {
        this.persisted = true;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public Map<String, Object> getChanges() {
        return changes;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    /** Who/where/when of an entry; {@code workspaceId} is null for platform-level entries. */
    public record Header(UUID workspaceId, UUID actorUserId, String actorLabel, String traceId, Instant occurredAt) {
    }
}

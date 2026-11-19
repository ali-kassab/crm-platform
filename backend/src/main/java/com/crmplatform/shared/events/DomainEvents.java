package com.crmplatform.shared.events;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module domain events. Business modules publish them; automation, audit, notifications and
 * realtime react (ADR-0001). Kept in {@code shared} so producers need no dependency on consumers.
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    /** Entity kinds that can carry tags/statuses and therefore trigger automation. */
    public enum TaggableType { CONTACT, PROJECT, JOB }

    /** Tags were added to or removed from an entity (BR-101/102). */
    public record TagsChanged(UUID workspaceId, TaggableType entityType, UUID entityId, Set<UUID> added,
            Set<UUID> removed, int chainDepth) {
        public TagsChanged {
            added = Set.copyOf(added);
            removed = Set.copyOf(removed);
        }
    }

    /** The status of an entity changed (NF-02). Either side may be {@code null}. */
    public record StatusChanged(UUID workspaceId, TaggableType entityType, UUID entityId, UUID previousStatusId,
            UUID newStatusId, int chainDepth) {
    }

    /** A taggable entity was deleted; its automation runs are cancelled. */
    public record TaggableDeleted(UUID workspaceId, TaggableType entityType, UUID entityId) {
    }

    /** A project was soft-deleted; dependents (jobs, notes, warranties, runs) cascade (BR-056). */
    public record ProjectDeleted(UUID workspaceId, UUID projectId) {
    }

    /** A job was soft-deleted; dependents (notes, warranties) cascade. */
    public record JobDeleted(UUID workspaceId, UUID jobId, UUID projectId) {
    }

    /** Audit trail entry (BR-111). {@code actorUserId} is null for automation-initiated changes. */
    public record AuditRecorded(UUID workspaceId, UUID actorUserId, String actorLabel, String action, String entityType,
            UUID entityId, UUID projectId, UUID jobId, Map<String, Object> changes) {
    }
}

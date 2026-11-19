package com.crmplatform.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base for workspace-owned, soft-deletable records. Hibernate fills {@code workspaceId} from the
 * active tenant on insert and adds it to every query (ADR-0004). Subclasses add
 * {@code @SQLRestriction("deleted_at is null")} to hide soft-deleted rows (ADR-0002).
 */
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @TenantId
    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted(Instant at, UUID by) {
        if (deletedAt == null) {
            this.deletedAt = at;
            this.deletedBy = by;
        }
    }
}

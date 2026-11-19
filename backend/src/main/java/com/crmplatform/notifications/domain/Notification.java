package com.crmplatform.notifications.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * One in-app notification for one user (directive R5). Language-neutral: the client renders the
 * Arabic or English sentence from {@link #getType()} and {@link #getPayload()}.
 */
@Entity
@Table(name = "notifications")
@SQLRestriction("deleted_at is null")
public class Notification extends TenantEntity {

    /** What happened. The client owns the wording for each type. */
    public enum Type {
        TASK_ASSIGNED,
        PROJECT_MEMBER_ADDED,
        PROJECT_MEMBER_REMOVED,
        PROJECT_MANAGER_ASSIGNED,
        PROJECT_DELETED,
        JOB_ASSIGNED,
        VISIT_SCHEDULED,
        VISIT_RESCHEDULED,
        WARRANTY_EXPIRING,
        MAINTENANCE_OVERDUE,
        MAINTENANCE_PLAN_ENDING
    }

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Column(nullable = false, length = 40, updatable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Type type;

    @Column(name = "entity_type", length = 20, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(UUID recipientUserId, Type type, String entityType, UUID entityId, UUID actorUserId,
            Map<String, Object> payload) {
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.entityType = entityType;
        this.entityId = entityId;
        this.actorUserId = actorUserId;
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public Type getType() {
        return type;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}

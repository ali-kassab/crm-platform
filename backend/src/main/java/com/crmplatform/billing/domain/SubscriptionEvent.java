package com.crmplatform.billing.domain;

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

/** Append-only history of a subscription (a database trigger rejects UPDATE and DELETE). */
@Entity
@Table(name = "subscription_events")
public class SubscriptionEvent implements Persistable<UUID> {

    public enum ActorKind { SYSTEM, ADMIN, TENANT }

    @Id
    private UUID id = UuidV7.generate();

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(nullable = false, length = 30, updatable = false)
    private String type;

    @Column(name = "from_status", length = 10, updatable = false)
    private String fromStatus;

    @Column(name = "to_status", length = 10, updatable = false)
    private String toStatus;

    @Column(name = "actor_kind", nullable = false, length = 8, updatable = false)
    private String actorKind;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Transient
    private boolean isNew = true;

    protected SubscriptionEvent() {
    }

    public SubscriptionEvent(Subscription s, String type, Subscription.Status from, ActorKind actor, UUID actorUserId,
            Map<String, Object> detail, Instant at) {
        this.workspaceId = s.getWorkspaceId();
        this.subscriptionId = s.getId();
        this.type = type;
        this.fromStatus = from == null ? null : from.name();
        this.toStatus = s.getStatus().name();
        this.actorKind = actor.name();
        this.actorUserId = actorUserId;
        this.detail = detail == null ? Map.of() : Map.copyOf(detail);
        this.occurredAt = at;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getType() {
        return type;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getActorKind() {
        return actorKind;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}

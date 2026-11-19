package com.crmplatform.realtime;

import com.crmplatform.notifications.NotificationEvents.NotificationsCreated;
import com.crmplatform.shared.events.DomainEvents.AuditRecorded;
import com.crmplatform.work.WorkEvents.BoardChanged;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes "something changed" hints to Redis after commit (ADR-0015). Messages carry ids only;
 * a failed publish is logged and never affects the business transaction (it already committed).
 */
@Component
class RealtimeRelay {

    /** Operational entities whose changes open screens should refresh for. */
    static final Set<String> LIVE_TYPES = Set.of("JOB", "VISIT", "MATERIAL", "APPROVAL", "EQUIPMENT",
            "MAINTENANCE_PLAN");

    private static final Logger log = LoggerFactory.getLogger(RealtimeRelay.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final RealtimeProperties properties;

    RealtimeRelay(StringRedisTemplate redis, ObjectMapper json, RealtimeProperties properties) {
        this.redis = redis;
        this.json = json;
        this.properties = properties;
    }

    @TransactionalEventListener
    void on(NotificationsCreated e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "notification");
        m.put("workspaceId", e.workspaceId().toString());
        m.put("userIds", e.userIds().stream().map(UUID::toString).sorted().toList());
        publish(m);
    }

    @TransactionalEventListener
    void on(AuditRecorded e) {
        if (e.workspaceId() == null || !LIVE_TYPES.contains(e.entityType())) {
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "changed");
        m.put("workspaceId", e.workspaceId().toString());
        m.put("entityType", e.entityType());
        m.put("entityId", e.entityId() == null ? null : e.entityId().toString());
        m.put("jobId", e.jobId() == null ? null : e.jobId().toString());
        m.put("action", e.action());
        publish(m);
    }

    /** Task-board changes (columns, tasks, comments): board id and change type only, never titles. */
    @TransactionalEventListener
    void on(BoardChanged e) {
        if (e.workspaceId() == null || e.boardId() == null) {
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "changed");
        m.put("workspaceId", e.workspaceId().toString());
        m.put("entityType", "BOARD");
        m.put("entityId", e.boardId().toString());
        m.put("jobId", null);
        m.put("action", e.type());
        publish(m);
    }

    private void publish(Map<String, Object> message) {
        if (!properties.enabled()) {
            return;
        }
        try {
            redis.convertAndSend(properties.channel(), json.writeValueAsString(message));
        } catch (RuntimeException ex) {
            log.warn("Realtime publish failed ({}); clients will catch up on their next fetch", ex.getMessage());
        }
    }
}

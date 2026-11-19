package com.crmplatform.shared.events;

import com.crmplatform.shared.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Convenience publisher for {@link DomainEvents.AuditRecorded} with change diffs. */
@Component
public class AuditTrail {

    private final ApplicationEventPublisher publisher;

    public AuditTrail(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void created(String entityType, UUID entityId, UUID projectId, UUID jobId, Map<String, ?> snapshot) {
        publish("CREATE", entityType, entityId, projectId, jobId, copy(snapshot));
    }

    public void updated(String entityType, UUID entityId, UUID projectId, UUID jobId, Map<String, ?> before,
            Map<String, ?> after) {
        Map<String, Object> changes = diff(before, after);
        if (!changes.isEmpty()) {
            publish("UPDATE", entityType, entityId, projectId, jobId, changes);
        }
    }

    public void deleted(String entityType, UUID entityId, UUID projectId, UUID jobId) {
        publish("DELETE", entityType, entityId, projectId, jobId, Map.of());
    }

    public void action(String action, String entityType, UUID entityId, UUID projectId, UUID jobId,
            Map<String, ?> details) {
        publish(action, entityType, entityId, projectId, jobId, copy(details));
    }

    /** Field-level diff: {@code field -> {from, to}} for every changed key. */
    public static Map<String, Object> diff(Map<String, ?> before, Map<String, ?> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String key : after.keySet()) {
            Object from = before.get(key);
            Object to = after.get(key);
            if (!Objects.equals(from, to)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("from", from);
                change.put("to", to);
                changes.put(key, change);
            }
        }
        return changes;
    }

    private void publish(String action, String entityType, UUID entityId, UUID projectId, UUID jobId,
            Map<String, Object> changes) {
        UUID workspaceId = TenantContext.requireWorkspaceId();
        boolean automated = AutomationChain.active();
        UUID actor = automated ? null : TenantContext.userId().orElse(null);
        String label = automated ? AutomationChain.runLabel() : null;
        publisher.publishEvent(new DomainEvents.AuditRecorded(workspaceId, actor, label, action, entityType, entityId,
                projectId, jobId, changes));
    }

    private static Map<String, Object> copy(Map<String, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((k, v) -> {
                if (v != null) {
                    copy.put(k, v);
                }
            });
        }
        return copy;
    }
}

package com.crmplatform.notifications.application;

import com.crmplatform.notifications.domain.Notification.Type;
import com.crmplatform.operations.OperationsEvents.JobAssigned;
import com.crmplatform.operations.OperationsEvents.MaintenanceOverdue;
import com.crmplatform.operations.OperationsEvents.MaintenancePlanEnding;
import com.crmplatform.operations.OperationsEvents.WarrantyExpiring;
import com.crmplatform.operations.OperationsEvents.VisitScheduled;
import com.crmplatform.shared.tenant.TenantWork;
import com.crmplatform.work.WorkEvents.ProjectDeletedNotice;
import com.crmplatform.work.WorkEvents.ProjectMembersChanged;
import com.crmplatform.work.WorkEvents.TaskAssigned;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns committed business events into notifications. Runs after the publishing transaction commits
 * (a rolled-back change notifies nobody), inside the event's workspace via {@link TenantWork}.
 */
@Component
class NotificationHandlers {

    private final NotificationService notifications;
    private final TenantWork tenantWork;

    NotificationHandlers(NotificationService notifications, TenantWork tenantWork) {
        this.notifications = notifications;
        this.tenantWork = tenantWork;
    }

    @TransactionalEventListener
    void on(TaskAssigned e) {
        send(e.workspaceId(), e.actorUserId(), e.userIds(), Type.TASK_ASSIGNED, "TASK", e.taskId(),
                payload("taskTitle", e.taskTitle(), "boardId", e.boardId()));
    }

    @TransactionalEventListener
    void on(ProjectMembersChanged e) {
        Map<String, Object> p = payload("projectName", e.projectName());
        tenantWork.run(e.workspaceId(), e.actorUserId(), () -> {
            notifications.notify(e.added(), Type.PROJECT_MEMBER_ADDED, "PROJECT", e.projectId(), e.actorUserId(), p);
            notifications.notify(e.removed(), Type.PROJECT_MEMBER_REMOVED, "PROJECT", e.projectId(), e.actorUserId(),
                    p);
            if (e.newManagerUserId() != null && !Objects.equals(e.newManagerUserId(), e.previousManagerUserId())) {
                notifications.notify(Set.of(e.newManagerUserId()), Type.PROJECT_MANAGER_ASSIGNED, "PROJECT",
                        e.projectId(), e.actorUserId(), p);
            }
        });
    }

    @TransactionalEventListener
    void on(ProjectDeletedNotice e) {
        send(e.workspaceId(), e.actorUserId(), e.memberUserIds(), Type.PROJECT_DELETED, "PROJECT", e.projectId(),
                payload("projectName", e.projectName()));
    }

    @TransactionalEventListener
    void on(JobAssigned e) {
        send(e.workspaceId(), e.actorUserId(), e.userIds(), Type.JOB_ASSIGNED, "JOB", e.jobId(),
                payload("jobTitle", e.jobTitle()));
    }

    @TransactionalEventListener
    void on(VisitScheduled e) {
        Map<String, Object> p = payload("jobId", e.jobId(), "jobTitle", e.jobTitle(),
                "scheduledStart", e.scheduledStart(), "scheduledEnd", e.scheduledEnd(),
                "rescheduledFromId", e.rescheduledFromId());
        send(e.workspaceId(), e.actorUserId(), e.userIds(),
                e.rescheduledFromId() == null ? Type.VISIT_SCHEDULED : Type.VISIT_RESCHEDULED, "VISIT", e.visitId(), p);
    }

    @TransactionalEventListener
    void on(WarrantyExpiring e) {
        send(e.workspaceId(), null, e.userIds(), Type.WARRANTY_EXPIRING, "WARRANTY", e.warrantyId(),
                payload("title", e.title(), "provider", e.provider(), "endDate", e.endDate(),
                        "equipmentId", e.equipmentId(), "contactId", e.contactId()));
    }

    @TransactionalEventListener
    void on(MaintenanceOverdue e) {
        send(e.workspaceId(), null, e.userIds(), Type.MAINTENANCE_OVERDUE, "JOB", e.jobId(),
                payload("planId", e.planId(), "planTitle", e.planTitle(), "dueDate", e.dueDate()));
    }

    @TransactionalEventListener
    void on(MaintenancePlanEnding e) {
        send(e.workspaceId(), null, e.userIds(), Type.MAINTENANCE_PLAN_ENDING, "MAINTENANCE_PLAN", e.planId(),
                payload("planTitle", e.planTitle(), "endDate", e.endDate(), "contactId", e.contactId()));
    }

    private void send(java.util.UUID workspaceId, java.util.UUID actor, Set<java.util.UUID> recipients, Type type,
            String entityType, java.util.UUID entityId, Map<String, Object> payload) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        tenantWork.run(workspaceId, actor,
                () -> notifications.notify(recipients, type, entityType, entityId, actor, payload));
    }

    /** Key/value pairs; null values are left out and ids/instants are stored as strings. */
    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object v = pairs[i + 1];
            if (v != null) {
                m.put((String) pairs[i], v instanceof String || v instanceof Number ? v : v.toString());
            }
        }
        return new HashMap<>(m);
    }
}

package com.crmplatform.operations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Events published by the operations module (consumed by notifications and, later, realtime). */
public final class OperationsEvents {

    private OperationsEvents() {
    }

    /** Technicians were newly assigned to a work order. {@code userIds} holds only the added users. */
    public record JobAssigned(UUID workspaceId, UUID jobId, String jobTitle, UUID actorUserId, Set<UUID> userIds) {
        public JobAssigned {
            userIds = Set.copyOf(userIds);
        }
    }

    /** A visit was scheduled (or rescheduled, then {@code rescheduledFromId} is set) for its assignees. */
    public record VisitScheduled(UUID workspaceId, UUID visitId, UUID jobId, String jobTitle, Instant scheduledStart,
            Instant scheduledEnd, UUID rescheduledFromId, UUID actorUserId, Set<UUID> userIds) {
        public VisitScheduled {
            userIds = Set.copyOf(userIds);
        }
    }

    /** A warranty enters its reminder window (sent once per end date). */
    public record WarrantyExpiring(UUID workspaceId, UUID warrantyId, String title, String provider,
            LocalDate endDate, UUID equipmentId, UUID contactId, Set<UUID> userIds) {
        public WarrantyExpiring {
            userIds = Set.copyOf(userIds);
        }
    }

    /** A preventive-maintenance job passed its due date without being completed (sent once). */
    public record MaintenanceOverdue(UUID workspaceId, UUID planId, String planTitle, UUID jobId, LocalDate dueDate,
            Set<UUID> userIds) {
        public MaintenanceOverdue {
            userIds = Set.copyOf(userIds);
        }
    }

    /** A maintenance agreement ends soon: time to renew it with the customer (sent once). */
    public record MaintenancePlanEnding(UUID workspaceId, UUID planId, String planTitle, UUID contactId,
            LocalDate endDate, Set<UUID> userIds) {
        public MaintenancePlanEnding {
            userIds = Set.copyOf(userIds);
        }
    }
}

package com.crmplatform.operations;

import java.time.Instant;
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
}

package com.crmplatform.work;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Events published by the work module (consumed by notifications and realtime). */
public final class WorkEvents {

    private WorkEvents() {
    }

    /** Team membership of a project changed (BR-057). Sets contain user ids. */
    public record ProjectMembersChanged(UUID workspaceId, UUID projectId, String projectName, UUID actorUserId,
            Set<UUID> added, Set<UUID> removed, UUID newManagerUserId, UUID previousManagerUserId) {
    }

    /** A project was deleted; members are informed (BR-110). */
    public record ProjectDeletedNotice(UUID workspaceId, UUID projectId, String projectName, UUID actorUserId,
            Set<UUID> memberUserIds) {
    }

    /** Users were assigned to a task (BR-110). */
    public record TaskAssigned(UUID workspaceId, UUID taskId, String taskTitle, UUID boardId, UUID actorUserId,
            Set<UUID> userIds) {
    }

    /** A committed board change, relayed to subscribers over STOMP (REALTIME_SPECIFICATION). */
    public record BoardChanged(UUID workspaceId, UUID boardId, long boardVersion, String type, UUID actorUserId,
            Map<String, Object> payload) {
    }
}

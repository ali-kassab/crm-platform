package com.crmplatform.notifications;

import java.util.Set;
import java.util.UUID;

/** Events of the notifications module (consumed by the realtime relay). */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    /** New notifications were stored for these users (published inside the storing transaction). */
    public record NotificationsCreated(UUID workspaceId, Set<UUID> userIds) {
        public NotificationsCreated {
            userIds = Set.copyOf(userIds);
        }
    }
}

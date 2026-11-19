package com.crmplatform.billing;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Events of the billing module (consumed by notifications). */
public final class BillingEvents {

    private BillingEvents() {
    }

    public enum Kind {
        SUBSCRIPTION_ENDING, SUBSCRIPTION_GRACE, SUBSCRIPTION_EXPIRED, SUBSCRIPTION_SUSPENDED,
        SUBSCRIPTION_ACTIVATED, REQUEST_APPROVED, REQUEST_REJECTED
    }

    /** Something about the workspace's subscription that its owners/admins should know. */
    public record SubscriptionNotice(UUID workspaceId, Kind kind, UUID subscriptionId, Set<UUID> userIds,
            Map<String, Object> payload) {
        public SubscriptionNotice {
            userIds = Set.copyOf(userIds);
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }
}

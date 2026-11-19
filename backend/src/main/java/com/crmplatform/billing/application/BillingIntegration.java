package com.crmplatform.billing.application;

import com.crmplatform.identity.WorkspaceCreated;
import com.crmplatform.shared.tenant.TenantWork;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Billing reactions: the trial starts with the workspace; a daily run moves subscriptions on. */
public final class BillingIntegration {

    private BillingIntegration() {
    }

    /** Same transaction as sign-up: a workspace never exists without a subscription. */
    @Component
    static class TrialOnSignUp {

        private final SubscriptionService subscriptions;

        TrialOnSignUp(SubscriptionService subscriptions) {
            this.subscriptions = subscriptions;
        }

        @EventListener
        void on(WorkspaceCreated event) {
            subscriptions.ensure(event.workspaceId());
        }
    }

    /** Daily (ShedLock): "ends soon" reminders, then GRACE and EXPIRED transitions. Idempotent. */
    @Component
    public static class SubscriptionLifecycle {

        private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycle.class);

        private final SubscriptionService subscriptions;
        private final TenantWork tenantWork;

        SubscriptionLifecycle(SubscriptionService subscriptions, TenantWork tenantWork) {
            this.subscriptions = subscriptions;
            this.tenantWork = tenantWork;
        }

        @Scheduled(cron = "${app.billing.lifecycle-cron:0 30 0 * * *}")
        @SchedulerLock(name = "subscription-lifecycle", lockAtMostFor = "PT30M")
        void scheduled() {
            runOnce();
        }

        public int runOnce() {
            int processed = 0;
            for (UUID id : tenantWork.callAsSystem(subscriptions::dueForLifecycle)) {
                try {
                    tenantWork.callAsSystem(() -> {
                        subscriptions.settle(id);
                        return null;
                    });
                    processed++;
                } catch (RuntimeException e) {
                    log.warn("Subscription lifecycle failed for {}", id, e);
                }
            }
            return processed;
        }
    }
}

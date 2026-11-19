package com.crmplatform.billing.application;

import com.crmplatform.billing.domain.BillingRepositories.PlanRepository;
import com.crmplatform.billing.domain.BillingRepositories.SubscriptionRepository;
import com.crmplatform.billing.domain.Plan;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.limits.UsageGuard;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Enforces the workspace plan's limits (D7); {@code null} limits mean unlimited. */
@Component
class PlanLimits implements UsageGuard {

    private final BillingProperties properties;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final UsageCounter counter;
    private final Clock clock;

    PlanLimits(BillingProperties properties, SubscriptionRepository subscriptions, PlanRepository plans,
            UsageCounter counter, Clock clock) {
        this.properties = properties;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.counter = counter;
        this.clock = clock;
    }

    @Override
    public void require(UUID workspaceId, Usage usage, long amount) {
        if (!properties.enforcementEnabled() || workspaceId == null) {
            return;
        }
        Plan plan = subscriptions.findByWorkspaceId(workspaceId).flatMap(s -> plans.findById(s.getPlanId()))
                .orElse(null);
        if (plan == null) {
            return;
        }
        Instant now = Instant.now(clock);
        Long limit = switch (usage) {
            case USERS -> asLong(plan.getMaxUsers());
            case ACTIVE_PROJECTS -> asLong(plan.getMaxActiveProjects());
            case JOBS_PER_MONTH -> asLong(plan.getMaxJobsPerMonth());
            case STORAGE_BYTES -> plan.getMaxStorageMb() == null ? null : plan.getMaxStorageMb() * 1024L * 1024L;
        };
        if (limit == null) {
            return;
        }
        long current = switch (usage) {
            case USERS -> counter.activeUsers(workspaceId);
            case ACTIVE_PROJECTS -> counter.activeProjects(workspaceId);
            case JOBS_PER_MONTH -> counter.jobsThisMonth(workspaceId, now);
            case STORAGE_BYTES -> counter.storageBytes(workspaceId);
        };
        if (current + amount > limit) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_LIMIT_REACHED",
                    "Your plan's limit has been reached; upgrade your plan to add more",
                    Map.of("limit", usage.name(), "max", limit, "current", current));
        }
    }

    private static Long asLong(Integer v) {
        return v == null ? null : v.longValue();
    }
}

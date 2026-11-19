package com.crmplatform.shared.limits;

import java.util.UUID;

/**
 * Subscription plan limits (D7). Business modules call it before adding something countable; the
 * billing module implements it. Without an implementation (or with enforcement disabled) nothing is
 * limited.
 */
public interface UsageGuard {

    enum Usage { USERS, ACTIVE_PROJECTS, JOBS_PER_MONTH, STORAGE_BYTES }

    /** Throws {@code PLAN_LIMIT_REACHED} when adding {@code amount} would exceed the workspace's plan. */
    void require(UUID workspaceId, Usage usage, long amount);
}

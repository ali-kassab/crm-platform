package com.crmplatform.billing.domain;

import com.crmplatform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A workspace's subscription (one per workspace, platform-level). Only the platform admin and the
 * system change it; a tenant can only ask (a request with a receipt). Expiry restricts writing but
 * never deletes data (D5).
 *
 * <p>Lifecycle: TRIAL or ACTIVE until {@code currentPeriodEnd} → GRACE (full access, warnings) for
 * the grace days → EXPIRED (read-only). SUSPENDED is set by the admin only (read-only).
 */
@Entity
@Table(name = "subscriptions")
public class Subscription extends BaseEntity {

    public enum Status {
        TRIAL, ACTIVE, GRACE, EXPIRED, SUSPENDED;

        public boolean canWrite() {
            return this == TRIAL || this == ACTIVE || this == GRACE;
        }
    }

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", length = 7)
    private BillingPeriod billingPeriod;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "grace_ends_at")
    private Instant graceEndsAt;

    /** The period end the "ends soon" reminder was sent for (once per period). */
    @Column(name = "ending_reminder_for")
    private Instant endingReminderFor;

    protected Subscription() {
    }

    public static Subscription trial(UUID workspaceId, UUID planId, Instant endsAt) {
        Subscription s = new Subscription();
        s.workspaceId = workspaceId;
        s.planId = planId;
        s.status = Status.TRIAL;
        s.currentPeriodEnd = endsAt;
        return s;
    }

    /**
     * The status as of {@code now}, even if the daily run has not stored it yet, so access never
     * depends on scheduler timing.
     */
    public Status effectiveStatus(Instant now, Duration grace) {
        if (status == Status.SUSPENDED || status == Status.EXPIRED) {
            return status;
        }
        if (now.isBefore(currentPeriodEnd)) {
            return status == Status.GRACE ? Status.ACTIVE : status;
        }
        return now.isBefore(currentPeriodEnd.plus(grace)) ? Status.GRACE : Status.EXPIRED;
    }

    /** Stores the effective status; returns true when it changed. */
    public boolean settle(Instant now, Duration grace) {
        Status next = effectiveStatus(now, grace);
        if (next == status) {
            return false;
        }
        status = next;
        graceEndsAt = next == Status.GRACE ? currentPeriodEnd.plus(grace) : null;
        return true;
    }

    /** A payment was approved: a new paid period. Remaining paid time is kept when renewing early. */
    public void activate(UUID planId, BillingPeriod period, Instant now) {
        Instant from = status == Status.ACTIVE && currentPeriodEnd.isAfter(now) ? currentPeriodEnd : now;
        this.planId = planId;
        this.billingPeriod = period;
        this.currentPeriodEnd = period.after(from);
        this.status = Status.ACTIVE;
        this.graceEndsAt = null;
    }

    /** Admin goodwill / correction: adds days to the current period (from now if it already ended). */
    public void extend(int days, Instant now, Duration grace) {
        Instant from = currentPeriodEnd.isAfter(now) ? currentPeriodEnd : now;
        currentPeriodEnd = from.plus(Duration.ofDays(days));
        if (status != Status.SUSPENDED) {
            status = status == Status.TRIAL ? Status.TRIAL : Status.ACTIVE;
            graceEndsAt = null;
        }
    }

    public void suspend() {
        status = Status.SUSPENDED;
        graceEndsAt = null;
    }

    /** Leaves SUSPENDED: back to whatever the period dates say. */
    public void reactivate(Instant now, Duration grace) {
        status = billingPeriod == null ? Status.TRIAL : Status.ACTIVE;
        settle(now, grace);
    }

    public void changePlan(UUID planId) {
        this.planId = planId;
    }

    public void endingReminded() {
        this.endingReminderFor = currentPeriodEnd;
    }

    public boolean endingReminderDue(Instant now, Duration before) {
        return (status == Status.TRIAL || status == Status.ACTIVE) && now.isBefore(currentPeriodEnd)
                && !now.isBefore(currentPeriodEnd.minus(before)) && !currentPeriodEnd.equals(endingReminderFor);
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public Status getStatus() {
        return status;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Instant getGraceEndsAt() {
        return graceEndsAt;
    }
}

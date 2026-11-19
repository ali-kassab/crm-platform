package com.crmplatform.billing.application;

import com.crmplatform.billing.BillingEvents.Kind;
import com.crmplatform.billing.BillingEvents.SubscriptionNotice;
import com.crmplatform.billing.domain.BillingPeriod;
import com.crmplatform.billing.domain.BillingRepositories.PaymentMethodRepository;
import com.crmplatform.billing.domain.BillingRepositories.PlanRepository;
import com.crmplatform.billing.domain.BillingRepositories.SubscriptionEventRepository;
import com.crmplatform.billing.domain.BillingRepositories.SubscriptionRepository;
import com.crmplatform.billing.domain.BillingRepositories.SubscriptionRequestRepository;
import com.crmplatform.billing.domain.PaymentMethod;
import com.crmplatform.billing.domain.Plan;
import com.crmplatform.billing.domain.Subscription;
import com.crmplatform.billing.domain.SubscriptionEvent;
import com.crmplatform.billing.domain.SubscriptionEvent.ActorKind;
import com.crmplatform.billing.domain.SubscriptionRequest;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.FilePurpose;
import com.crmplatform.files.Files;
import com.crmplatform.files.Files.FileSummary;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant side of billing: the workspace's subscription, its access mode, and payment requests.
 * A tenant can never change its own status — it can only submit a request for the admin to review.
 */
@Service
public class SubscriptionService {

    public static final String RECEIPT_OWNER = "SUBSCRIPTION_REQUEST";

    private final SubscriptionRepository subscriptions;
    private final SubscriptionRequestRepository requests;
    private final SubscriptionEventRepository history;
    private final PlanRepository plans;
    private final PaymentMethodRepository methods;
    private final UsageCounter counter;
    private final Files files;
    private final RateLimiter rateLimiter;
    private final BillingProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptions, SubscriptionRequestRepository requests,
            SubscriptionEventRepository history, PlanRepository plans, PaymentMethodRepository methods,
            UsageCounter counter, Files files, RateLimiter rateLimiter, BillingProperties properties,
            ApplicationEventPublisher events, Clock clock) {
        this.subscriptions = subscriptions;
        this.requests = requests;
        this.history = history;
        this.plans = plans;
        this.methods = methods;
        this.counter = counter;
        this.files = files;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ access

    /** Whether the workspace may write (create/edit/delete). Read access is never removed. */
    @Transactional(readOnly = true)
    public boolean canWrite(UUID workspaceId) {
        if (!properties.enforcementEnabled()) {
            return true;
        }
        return subscriptions.findByWorkspaceId(workspaceId)
                .map(s -> s.effectiveStatus(Instant.now(clock), grace()).canWrite())
                .orElse(true); // no row yet: it is created on first read; never lock a workspace out by accident
    }

    /** Creates the trial for a new workspace (idempotent). */
    @Transactional
    public Subscription ensure(UUID workspaceId) {
        return subscriptions.findByWorkspaceId(workspaceId).orElseGet(() -> {
            Plan plan = plans.findByCode(properties.trialPlanCode())
                    .or(() -> plans.findByActiveTrueOrderBySortOrderAsc().stream().findFirst())
                    .orElseThrow(() -> new IllegalStateException("No billing plan configured"));
            Instant now = Instant.now(clock);
            Subscription s = subscriptions.save(
                    Subscription.trial(workspaceId, plan.getId(), now.plus(Duration.ofDays(properties.trialDays()))));
            history.save(new SubscriptionEvent(s, "TRIAL_STARTED", null, ActorKind.SYSTEM, null,
                    Map.of("planCode", plan.getCode(), "days", properties.trialDays()), now));
            return s;
        });
    }

    // ------------------------------------------------------------------ tenant views

    @Transactional
    public Overview overview(UUID workspaceId) {
        Subscription s = ensure(workspaceId);
        Instant now = Instant.now(clock);
        Plan plan = plans.findById(s.getPlanId()).orElseThrow();
        Subscription.Status status = properties.enforcementEnabled() ? s.effectiveStatus(now, grace())
                : Subscription.Status.ACTIVE;
        Instant graceEnds = status == Subscription.Status.GRACE ? s.getCurrentPeriodEnd().plus(grace()) : null;
        long daysLeft = Math.max(0, Duration.between(now, s.getCurrentPeriodEnd()).toDays());
        SubscriptionRequest pending = requests.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .filter(SubscriptionRequest::isPending).findFirst().orElse(null);
        return new Overview(s, status, plan, graceEnds, daysLeft, !status.canWrite(), counter.of(workspaceId, now),
                pending);
    }

    @Transactional(readOnly = true)
    public List<Plan> activePlans() {
        return plans.findByActiveTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<PaymentMethod> activeMethods() {
        return methods.findByActiveTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRequest> requestsOf(UUID workspaceId) {
        return requests.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    // ------------------------------------------------------------------ tenant commands

    /** "I paid": records the transfer and receipt for the platform admin to review. */
    @Transactional
    public SubscriptionRequest submit(UUID workspaceId, UUID userId, RequestData d) {
        rateLimiter.check("subscription-request:" + workspaceId, 10, Duration.ofHours(1));
        Subscription s = ensure(workspaceId);
        if (s.getStatus() == Subscription.Status.SUSPENDED) {
            throw ApiException.conflict("WORKSPACE_SUSPENDED",
                    "This workspace is suspended; contact support to reactivate it");
        }
        if (requests.existsByWorkspaceIdAndStatus(workspaceId, SubscriptionRequest.Status.PENDING)) {
            throw ApiException.conflict("REQUEST_ALREADY_PENDING",
                    "A payment request is already waiting for review");
        }
        Plan plan = plans.findById(d.planId()).filter(Plan::isActive)
                .orElseThrow(() -> ApiException.referenceNotFound("planId"));
        PaymentMethod method = methods.findById(d.paymentMethodId()).filter(PaymentMethod::isActive)
                .orElseThrow(() -> ApiException.referenceNotFound("paymentMethodId"));
        BillingPeriod period = parsePeriod(d.billingPeriod());
        LocalDate today = LocalDate.now(clock);
        if (d.paidOn() == null || d.paidOn().isAfter(today) || d.paidOn().isBefore(today.minusDays(60))) {
            throw ApiException.badRequest("VALIDATION_ERROR", "paidOn must be within the last 60 days");
        }
        if (d.payerReference() == null || d.payerReference().isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "payerReference is required");
        }
        FileSummary receipt = files.summaries(Set.of(d.receiptFileId())).get(d.receiptFileId());
        if (receipt == null || receipt.purpose() != FilePurpose.RECEIPT) {
            throw ApiException.badRequest("RECEIPT_REQUIRED", "Upload the payment receipt first (purpose RECEIPT)");
        }
        long usersNow = counter.activeUsers(workspaceId);
        if (plan.getMaxUsers() != null && usersNow > plan.getMaxUsers()) {
            throw ApiException.conflict("PLAN_TOO_SMALL",
                    "This plan allows fewer users than the workspace has; choose a larger plan");
        }
        SubscriptionRequest r = new SubscriptionRequest(workspaceId, plan, period, method.getId(), d.payerReference(),
                d.paidOn(), d.receiptFileId(), d.note(), userId);
        SubscriptionRequest saved = requests.saveAndFlush(r);
        files.attach(List.of(d.receiptFileId()), receipt.kind() == FileKind.IMAGE ? FileKind.IMAGE : FileKind.DOCUMENT,
                RECEIPT_OWNER, saved.getId(), "receiptFileId");
        history.save(new SubscriptionEvent(s, "REQUEST_SUBMITTED", s.getStatus(), ActorKind.TENANT, userId,
                Map.of("requestId", saved.getId().toString(), "planCode", plan.getCode(), "period", period.name(),
                        "amount", saved.getAmount().toPlainString()), Instant.now(clock)));
        return saved;
    }

    @Transactional
    public SubscriptionRequest cancel(UUID workspaceId, UUID userId, UUID requestId) {
        SubscriptionRequest r = requests.findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Subscription request"));
        if (!r.isPending()) {
            throw ApiException.conflict("REQUEST_NOT_PENDING", "Only a pending request can be changed");
        }
        r.cancel();
        Subscription s = ensure(workspaceId);
        history.save(new SubscriptionEvent(s, "REQUEST_CANCELLED", s.getStatus(), ActorKind.TENANT, userId,
                Map.of("requestId", requestId.toString()), Instant.now(clock)));
        return requests.saveAndFlush(r);
    }

    // ------------------------------------------------------------------ system (daily run)

    @Transactional(readOnly = true)
    public List<UUID> dueForLifecycle() {
        Instant cutoff = Instant.now(clock).plus(properties.reminderBefore());
        return subscriptions.findByCurrentPeriodEndBefore(cutoff).stream().map(Subscription::getId).toList();
    }

    /** Reminder before the end, then GRACE, then EXPIRED — each notice sent once. */
    @Transactional
    public void settle(UUID subscriptionId) {
        Subscription s = subscriptions.findById(subscriptionId).orElse(null);
        if (s == null) {
            return;
        }
        Instant now = Instant.now(clock);
        Set<UUID> managers = new HashSet<>(counter.managers(s.getWorkspaceId()));
        if (s.endingReminderDue(now, properties.reminderBefore())) {
            s.endingReminded();
            notice(s, Kind.SUBSCRIPTION_ENDING, managers, Map.of("endsAt", s.getCurrentPeriodEnd().toString()));
        }
        Subscription.Status before = s.getStatus();
        if (properties.enforcementEnabled() && s.settle(now, grace())) {
            history.save(new SubscriptionEvent(s, "STATUS_CHANGED", before, ActorKind.SYSTEM, null, Map.of(), now));
            if (s.getStatus() == Subscription.Status.GRACE) {
                notice(s, Kind.SUBSCRIPTION_GRACE, managers, Map.of("graceEndsAt", s.getGraceEndsAt().toString()));
            } else if (s.getStatus() == Subscription.Status.EXPIRED) {
                notice(s, Kind.SUBSCRIPTION_EXPIRED, managers, Map.of());
            }
        }
        subscriptions.saveAndFlush(s);
    }

    void notice(Subscription s, Kind kind, Set<UUID> recipients, Map<String, Object> payload) {
        if (!recipients.isEmpty()) {
            events.publishEvent(new SubscriptionNotice(s.getWorkspaceId(), kind, s.getId(), recipients, payload));
        }
    }

    Duration grace() {
        return Duration.ofDays(properties.graceDays());
    }

    static BillingPeriod parsePeriod(String v) {
        try {
            return BillingPeriod.valueOf(v);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "billingPeriod must be MONTHLY or YEARLY");
        }
    }

    public record RequestData(UUID planId, String billingPeriod, UUID paymentMethodId, String payerReference,
            LocalDate paidOn, UUID receiptFileId, String note) {
    }

    public record Overview(Subscription subscription, Subscription.Status status, Plan plan, Instant graceEndsAt,
            long daysLeft, boolean readOnly, UsageCounter.Usage usage, SubscriptionRequest pendingRequest) {
    }
}

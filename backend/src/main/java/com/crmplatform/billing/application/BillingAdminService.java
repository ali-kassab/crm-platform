package com.crmplatform.billing.application;

import com.crmplatform.billing.BillingEvents.Kind;
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
import com.crmplatform.files.Files;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.tenant.TenantWork;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform admin: plans, payment instructions, request review and per-workspace subscription actions. */
@Service
public class BillingAdminService {

    private final PlanRepository plans;
    private final PaymentMethodRepository methods;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionRequestRepository requests;
    private final SubscriptionEventRepository history;
    private final SubscriptionService tenantSide;
    private final UsageCounter counter;
    private final Files files;
    private final TenantWork tenantWork;
    private final AuditTrail audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public BillingAdminService(PlanRepository plans, PaymentMethodRepository methods,
            SubscriptionRepository subscriptions, SubscriptionRequestRepository requests,
            SubscriptionEventRepository history, SubscriptionService tenantSide, UsageCounter counter, Files files,
            TenantWork tenantWork, AuditTrail audit, JdbcTemplate jdbc, Clock clock) {
        this.plans = plans;
        this.methods = methods;
        this.subscriptions = subscriptions;
        this.requests = requests;
        this.history = history;
        this.tenantSide = tenantSide;
        this.counter = counter;
        this.files = files;
        this.tenantWork = tenantWork;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ plans

    @Transactional(readOnly = true)
    public List<Plan> plans() {
        return plans.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public Plan createPlan(UUID adminId, PlanData d) {
        if (d.code() == null || !d.code().matches("^[A-Za-z0-9_]{2,30}$")) {
            throw ApiException.badRequest("VALIDATION_ERROR", "code must be 2-30 letters, digits or underscores");
        }
        if (plans.existsByCode(d.code().toUpperCase(java.util.Locale.ROOT))) {
            throw ApiException.conflict("PLAN_CODE_TAKEN", "A plan with this code already exists");
        }
        Plan p = new Plan(d.code());
        apply(p, d, true);
        Plan saved = plans.save(p);
        audit.platform("PLAN_CREATED", "PLAN", saved.getId(), adminId, null, planSnapshot(saved));
        return saved;
    }

    @Transactional
    public Plan updatePlan(UUID adminId, UUID id, PlanData d) {
        Plan p = plans.findById(id).orElseThrow(() -> ApiException.notFound("Plan"));
        Map<String, Object> before = planSnapshot(p);
        apply(p, d, false);
        plans.saveAndFlush(p);
        audit.platform("PLAN_UPDATED", "PLAN", id, adminId, null, AuditTrail.diff(before, planSnapshot(p)));
        return p;
    }

    private static void apply(Plan p, PlanData d, boolean creating) {
        if (creating || d.nameAr() != null || d.nameEn() != null) {
            p.name(required(d.nameAr() == null ? p.getNameAr() : d.nameAr(), "nameAr"),
                    required(d.nameEn() == null ? p.getNameEn() : d.nameEn(), "nameEn"));
        }
        if (creating || d.descriptionAr() != null || d.descriptionEn() != null) {
            p.describe(d.descriptionAr() == null ? p.getDescriptionAr() : d.descriptionAr(),
                    d.descriptionEn() == null ? p.getDescriptionEn() : d.descriptionEn());
        }
        if (creating || d.priceMonthly() != null || d.priceYearly() != null) {
            BigDecimal monthly = d.priceMonthly() == null ? p.getPriceMonthly() : d.priceMonthly();
            BigDecimal yearly = d.priceYearly() == null ? p.getPriceYearly() : d.priceYearly();
            p.price(required(monthly, "priceMonthly"), required(yearly, "priceYearly"), p.getCurrency());
        }
        if (creating || d.limitsGiven()) {
            p.limits(d.maxUsers(), d.maxActiveProjects(), d.maxJobsPerMonth(), d.maxStorageMb());
        }
        if (d.active() != null) {
            p.setActive(d.active());
        }
        if (d.sortOrder() != null) {
            p.setSortOrder(d.sortOrder());
        }
    }

    // ------------------------------------------------------------------ payment methods

    @Transactional(readOnly = true)
    public List<PaymentMethod> methods() {
        return methods.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public PaymentMethod saveMethod(UUID adminId, UUID id, MethodData d) {
        PaymentMethod m = id == null ? new PaymentMethod(parseKind(required(d.kind(), "kind")))
                : methods.findById(id).orElseThrow(() -> ApiException.notFound("Payment method"));
        if (id != null && d.kind() != null) {
            m.setKind(parseKind(d.kind()));
        }
        if (id == null || d.labelAr() != null || d.labelEn() != null) {
            m.label(required(d.labelAr() == null ? m.getLabelAr() : d.labelAr(), "labelAr"),
                    required(d.labelEn() == null ? m.getLabelEn() : d.labelEn(), "labelEn"));
        }
        if (id == null || d.account() != null) {
            m.setAccount(required(d.account(), "account"));
        }
        if (id == null || d.instructionsAr() != null || d.instructionsEn() != null) {
            m.instructions(d.instructionsAr() == null ? m.getInstructionsAr() : d.instructionsAr(),
                    d.instructionsEn() == null ? m.getInstructionsEn() : d.instructionsEn());
        }
        if (d.active() != null) {
            m.setActive(d.active());
        }
        if (d.sortOrder() != null) {
            m.setSortOrder(d.sortOrder());
        }
        PaymentMethod saved = methods.saveAndFlush(m);
        audit.platform(id == null ? "PAYMENT_METHOD_CREATED" : "PAYMENT_METHOD_UPDATED", "PAYMENT_METHOD",
                saved.getId(), adminId, null, Map.of("kind", saved.getKind().name(), "active", saved.isActive()));
        return saved;
    }

    // ------------------------------------------------------------------ requests

    @Transactional(readOnly = true)
    public Page<SubscriptionRequest> requests(SubscriptionRequest.Status status, Pageable pageable) {
        return status == null ? requests.findAll(pageable) : requests.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public SubscriptionRequest request(UUID id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Subscription request"));
    }

    /** The receipt lives in the tenant's storage; read it in that workspace's scope. */
    public String receiptUrl(UUID adminId, UUID requestId) {
        SubscriptionRequest r = request(requestId);
        return tenantWork.call(r.getWorkspaceId(), adminId,
                () -> files.downloadUrlOfOwned(r.getReceiptFileId(), SubscriptionService.RECEIPT_OWNER, r.getId()));
    }

    @Transactional
    public SubscriptionRequest approve(UUID adminId, UUID requestId, String note) {
        SubscriptionRequest r = pending(requestId);
        Subscription s = tenantSide.ensure(r.getWorkspaceId());
        if (s.getStatus() == Subscription.Status.SUSPENDED) {
            throw ApiException.conflict("WORKSPACE_SUSPENDED", "Reactivate the workspace before approving a payment");
        }
        Instant now = Instant.now(clock);
        Subscription.Status before = s.getStatus();
        s.activate(r.getPlanId(), r.getBillingPeriod(), now);
        r.review(SubscriptionRequest.Status.APPROVED, adminId, note, now);
        subscriptions.saveAndFlush(s);
        requests.saveAndFlush(r);
        Map<String, Object> detail = Map.of("requestId", r.getId().toString(), "period", r.getBillingPeriod().name(),
                "amount", r.getAmount().toPlainString(), "periodEnd", s.getCurrentPeriodEnd().toString());
        history.save(new SubscriptionEvent(s, "PAYMENT_APPROVED", before, ActorKind.ADMIN, adminId, detail, now));
        audit.platform("SUBSCRIPTION_APPROVED", "SUBSCRIPTION_REQUEST", r.getId(), adminId, null, detail);
        tenantSide.notice(s, Kind.REQUEST_APPROVED, recipients(r), Map.of("periodEnd",
                s.getCurrentPeriodEnd().toString(), "planId", s.getPlanId().toString()));
        return r;
    }

    @Transactional
    public SubscriptionRequest reject(UUID adminId, UUID requestId, String reason) {
        SubscriptionRequest r = pending(requestId);
        Instant now = Instant.now(clock);
        r.review(SubscriptionRequest.Status.REJECTED, adminId, required(reason, "reason"), now);
        requests.saveAndFlush(r);
        Subscription s = tenantSide.ensure(r.getWorkspaceId());
        history.save(new SubscriptionEvent(s, "PAYMENT_REJECTED", s.getStatus(), ActorKind.ADMIN, adminId,
                Map.of("requestId", r.getId().toString(), "reason", reason), now));
        audit.platform("SUBSCRIPTION_REJECTED", "SUBSCRIPTION_REQUEST", r.getId(), adminId, null,
                Map.of("reason", reason));
        tenantSide.notice(s, Kind.REQUEST_REJECTED, recipients(r), Map.of("reason", reason));
        return r;
    }

    private SubscriptionRequest pending(UUID requestId) {
        SubscriptionRequest r = request(requestId);
        if (!r.isPending()) {
            throw ApiException.conflict("REQUEST_NOT_PENDING", "Only a pending request can be changed");
        }
        return r;
    }

    private Set<UUID> recipients(SubscriptionRequest r) {
        Set<UUID> ids = new HashSet<>(counter.managers(r.getWorkspaceId()));
        ids.add(r.getRequestedBy());
        return ids;
    }

    // ------------------------------------------------------------------ workspaces

    @Transactional(readOnly = true)
    public List<WorkspaceRow> workspaces(String status, String q, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                select w.id, w.name, w.slug, w.created_at, w.deleted_at, s.status, s.current_period_end, p.code,
                       (select count(*) from memberships m where m.workspace_id = w.id and m.active) as members,
                       (select u.email from memberships m join users u on u.id = m.user_id
                         where m.workspace_id = w.id and m.role = 'OWNER' and m.active
                         order by m.joined_at limit 1) as owner_email
                from workspaces w
                left join subscriptions s on s.workspace_id = w.id
                left join billing_plans p on p.id = s.plan_id
                where 1 = 1
                """);
        List<Object> args = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" and s.status = ?");
            args.add(status);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" and (app_search_norm(w.name) like ? or lower(w.slug) like ?)");
            String like = com.crmplatform.shared.web.Pagination.likePattern(q);
            args.add(like);
            args.add(like);
        }
        sql.append(" order by w.created_at desc limit ? offset ?");
        args.add(Math.min(Math.max(limit, 1), 200));
        args.add(Math.max(offset, 0));
        return jdbc.query(sql.toString(), (rs, i) -> new WorkspaceRow(rs.getObject("id", UUID.class),
                rs.getString("name"), rs.getString("slug"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant(),
                rs.getString("status"),
                rs.getTimestamp("current_period_end") == null ? null : rs.getTimestamp("current_period_end").toInstant(),
                rs.getString("code"), rs.getLong("members"), rs.getString("owner_email")), args.toArray());
    }

    @Transactional
    public WorkspaceDetail workspace(UUID workspaceId) {
        Integer exists = jdbc.queryForObject("select count(*) from workspaces where id = ?", Integer.class, workspaceId);
        if (exists == null || exists == 0) {
            throw ApiException.notFound("Workspace");
        }
        SubscriptionService.Overview overview = tenantSide.overview(workspaceId);
        return new WorkspaceDetail(workspaceId, overview, history.findByWorkspaceIdOrderByOccurredAtDesc(workspaceId),
                requests.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId));
    }

    @Transactional
    public Subscription act(UUID adminId, UUID workspaceId, Action action, Integer days, UUID planId, String note) {
        workspace(workspaceId); // 404 for unknown workspaces
        Subscription s = tenantSide.ensure(workspaceId);
        Instant now = Instant.now(clock);
        Subscription.Status before = s.getStatus();
        Map<String, Object> detail = new LinkedHashMap<>();
        if (note != null) {
            detail.put("note", note);
        }
        switch (action) {
            case EXTEND -> {
                if (days == null || days < 1 || days > 366) {
                    throw ApiException.badRequest("VALIDATION_ERROR", "days must be between 1 and 366");
                }
                s.extend(days, now, tenantSide.grace());
                detail.put("days", days);
            }
            case SUSPEND -> {
                required(note, "note");
                s.suspend();
            }
            case REACTIVATE -> {
                if (s.getStatus() != Subscription.Status.SUSPENDED) {
                    throw ApiException.conflict("NOT_SUSPENDED", "The workspace is not suspended");
                }
                s.reactivate(now, tenantSide.grace());
            }
            case CHANGE_PLAN -> {
                Plan plan = plans.findById(planId == null ? UUID.randomUUID() : planId)
                        .orElseThrow(() -> ApiException.referenceNotFound("planId"));
                s.changePlan(plan.getId());
                detail.put("planCode", plan.getCode());
            }
        }
        detail.put("periodEnd", s.getCurrentPeriodEnd().toString());
        subscriptions.saveAndFlush(s);
        history.save(new SubscriptionEvent(s, action.name(), before, ActorKind.ADMIN, adminId, detail, now));
        audit.platform("SUBSCRIPTION_" + action.name(), "SUBSCRIPTION", s.getId(), adminId, null, detail);
        Set<UUID> managers = new HashSet<>(counter.managers(workspaceId));
        if (action == Action.SUSPEND) {
            tenantSide.notice(s, Kind.SUBSCRIPTION_SUSPENDED, managers, Map.of());
        } else if (!before.canWrite() && s.getStatus().canWrite()) {
            tenantSide.notice(s, Kind.SUBSCRIPTION_ACTIVATED, managers,
                    Map.of("periodEnd", s.getCurrentPeriodEnd().toString()));
        }
        return s;
    }

    // ------------------------------------------------------------------ helpers

    private static PaymentMethod.Kind parseKind(String v) {
        try {
            return PaymentMethod.Kind.valueOf(v);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown payment method kind");
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw ApiException.badRequest("VALIDATION_ERROR", field + " is required");
        }
        return value;
    }

    private static Map<String, Object> planSnapshot(Plan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", p.getCode());
        m.put("priceMonthly", p.getPriceMonthly().toPlainString());
        m.put("priceYearly", p.getPriceYearly().toPlainString());
        m.put("maxUsers", p.getMaxUsers());
        m.put("maxActiveProjects", p.getMaxActiveProjects());
        m.put("maxJobsPerMonth", p.getMaxJobsPerMonth());
        m.put("maxStorageMb", p.getMaxStorageMb());
        m.put("active", p.isActive());
        return m;
    }

    public enum Action { EXTEND, SUSPEND, REACTIVATE, CHANGE_PLAN }

    public record PlanData(String code, String nameAr, String nameEn, String descriptionAr, String descriptionEn,
            BigDecimal priceMonthly, BigDecimal priceYearly, Integer maxUsers, Integer maxActiveProjects,
            Integer maxJobsPerMonth, Integer maxStorageMb, boolean limitsGiven, Boolean active, Integer sortOrder) {
    }

    public record MethodData(String kind, String labelAr, String labelEn, String account, String instructionsAr,
            String instructionsEn, Boolean active, Integer sortOrder) {
    }

    public record WorkspaceRow(UUID id, String name, String slug, Instant createdAt, Instant deletedAt, String status,
            Instant currentPeriodEnd, String planCode, long members, String ownerEmail) {
    }

    public record WorkspaceDetail(UUID workspaceId, SubscriptionService.Overview overview,
            List<SubscriptionEvent> history, List<SubscriptionRequest> requests) {
    }
}

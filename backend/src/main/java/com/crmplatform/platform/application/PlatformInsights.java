package com.crmplatform.platform.application;

import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only platform figures for the platform admin. Every tenant figure is an aggregate computed
 * in the database (counts and byte sums): no query here selects a tenant's business columns —
 * customer names, phones, notes, descriptions, amounts of tenant transactions or file contents.
 * Company names, owner emails, plans and subscription dates are the administrative metadata the
 * platform needs to run the service.
 *
 * <p>Platform-level tables are not tenant-filtered (no {@code @TenantId}), so this module reads
 * them with explicit SQL, the same way the billing module's usage counter does.
 */
@Service
public class PlatformInsights {

    /** Effective subscription status, mirroring {@code Subscription.effectiveStatus}. */
    private static final String EFFECTIVE_STATUS = """
            case when s.id is null then null
                 when not :enforcement then case when s.status = 'SUSPENDED' then 'SUSPENDED' else 'ACTIVE' end
                 when s.status in ('SUSPENDED', 'EXPIRED') then s.status
                 when :now < s.current_period_end then case when s.status = 'GRACE' then 'ACTIVE' else s.status end
                 when :now < s.current_period_end + make_interval(days => :grace) then 'GRACE'
                 else 'EXPIRED' end""";

    /** Per-workspace aggregates (counts only), joined to the company rows. */
    private static final String USAGE_CTES = """
            with usr as (select m.workspace_id, count(*) as members,
                                count(*) filter (where m.active and u.active) as active_users
                         from memberships m join users u on u.id = m.user_id group by m.workspace_id),
                 cus as (select workspace_id, count(*) as n from contacts where deleted_at is null group by workspace_id),
                 prj as (select workspace_id, count(*) as n from projects where deleted_at is null group by workspace_id),
                 job as (select workspace_id, count(*) as n, count(*) filter (where created_at >= :monthStart) as month
                         from jobs where deleted_at is null group by workspace_id),
                 sto as (select workspace_id, count(*) as files, coalesce(sum(size_bytes), 0) as bytes
                         from stored_files where deleted_at is null group by workspace_id),
                 co as (select w.id, w.name, w.slug, w.created_at, w.deleted_at, s.current_period_end,
                               s.billing_period, p.code as plan_code, p.name_en as plan_name_en,
                               p.name_ar as plan_name_ar, """ + EFFECTIVE_STATUS + """
             as status
                        from workspaces w
                        left join subscriptions s on s.workspace_id = w.id
                        left join billing_plans p on p.id = s.plan_id)
            """;

    private static final String COMPANY_COLUMNS = """
            select co.*, coalesce(usr.members, 0) as members, coalesce(usr.active_users, 0) as active_users,
                   coalesce(cus.n, 0) as customers, coalesce(prj.n, 0) as projects, coalesce(job.n, 0) as work_orders,
                   coalesce(job.month, 0) as work_orders_month, coalesce(sto.files, 0) as files,
                   coalesce(sto.bytes, 0) as storage_bytes,
                   (select u.email from memberships m join users u on u.id = m.user_id
                     where m.workspace_id = co.id and m.role = 'OWNER' and m.active
                     order by m.joined_at limit 1) as owner_email
            from co
            left join usr on usr.workspace_id = co.id
            left join cus on cus.workspace_id = co.id
            left join prj on prj.workspace_id = co.id
            left join job on job.workspace_id = co.id
            left join sto on sto.workspace_id = co.id
            """;

    /** API sort field → SQL expression (fixed whitelist, never user text). */
    private static final Map<String, String> COMPANY_SORT = Map.ofEntries(
            Map.entry("createdAt", "co.created_at"), Map.entry("name", "lower(co.name)"),
            Map.entry("status", "co.status"), Map.entry("periodEnd", "co.current_period_end"),
            Map.entry("users", "coalesce(usr.active_users, 0)"), Map.entry("customers", "coalesce(cus.n, 0)"),
            Map.entry("projects", "coalesce(prj.n, 0)"), Map.entry("workOrders", "coalesce(job.n, 0)"),
            Map.entry("storage", "coalesce(sto.bytes, 0)"));

    private static final Set<String> STATUS_FILTERS = Set.of("TRIAL", "ACTIVE", "GRACE", "EXPIRED", "SUSPENDED",
            "OPERATIONAL", "DELETED");

    private static final Map<String, String> AUDIT_SORT = Map.of("occurredAt", "a.occurred_at");

    private final NamedParameterJdbcTemplate jdbc;
    private final ApiUsageMeter apiUsage;
    private final ObjectMapper json;
    private final Clock clock;
    private final int graceDays;
    private final boolean enforcement;

    public PlatformInsights(NamedParameterJdbcTemplate jdbc, ApiUsageMeter apiUsage, ObjectMapper json, Clock clock,
            @Value("${app.billing.grace-days:7}") int graceDays,
            @Value("${app.billing.enforcement-enabled:true}") boolean enforcement) {
        this.jdbc = jdbc;
        this.apiUsage = apiUsage;
        this.json = json;
        this.clock = clock;
        this.graceDays = graceDays;
        this.enforcement = enforcement;
    }

    // ------------------------------------------------------------------ dashboard

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Instant now = Instant.now(clock);
        MapSqlParameterSource args = base(now).addValue("since", Timestamp.from(now.minus(Duration.ofDays(30))))
                .addValue("soon", Timestamp.from(now.plus(Duration.ofDays(7))));

        Map<String, Object> c = jdbc.queryForMap(USAGE_CTES + """
                select count(*) as total,
                       count(*) filter (where deleted_at is null and status in ('TRIAL', 'ACTIVE', 'GRACE')) as operational,
                       count(*) filter (where deleted_at is null and status = 'TRIAL') as trial,
                       count(*) filter (where deleted_at is null and status = 'ACTIVE') as paid,
                       count(*) filter (where deleted_at is null and status = 'GRACE') as grace,
                       count(*) filter (where deleted_at is null and status = 'EXPIRED') as expired,
                       count(*) filter (where deleted_at is null and status = 'SUSPENDED') as suspended,
                       count(*) filter (where deleted_at is not null) as deleted,
                       count(*) filter (where created_at >= :since) as new_last_30_days,
                       count(*) filter (where deleted_at is null and status in ('TRIAL', 'ACTIVE')
                                        and current_period_end < :soon) as ending_within_7_days
                from co""", args);
        CompanyCounts companies = new CompanyCounts(l(c, "total"), l(c, "operational"), l(c, "trial"), l(c, "paid"),
                l(c, "grace"), l(c, "expired"), l(c, "suspended"), l(c, "deleted"), l(c, "new_last_30_days"),
                l(c, "ending_within_7_days"));

        Map<String, Object> u = jdbc.queryForMap("""
                select count(*) filter (where not super_admin) as total,
                       count(*) filter (where not super_admin and active) as active,
                       count(*) filter (where not super_admin and not active) as inactive,
                       count(*) filter (where super_admin) as platform_admins,
                       count(*) filter (where not super_admin and created_at >= :since) as new_last_30_days,
                       count(*) filter (where not super_admin and last_login_at >= :since) as signed_in_last_30_days
                from users""", args);
        UserCounts users = new UserCounts(l(u, "total"), l(u, "active"), l(u, "inactive"), l(u, "platform_admins"),
                l(u, "new_last_30_days"), l(u, "signed_in_last_30_days"));

        Map<String, Object> t = jdbc.queryForMap("""
                select (select count(*) from contacts where deleted_at is null) as customers,
                       (select count(*) from projects where deleted_at is null) as projects,
                       (select count(*) from jobs where deleted_at is null) as work_orders,
                       (select count(*) from stored_files where deleted_at is null) as files,
                       (select coalesce(sum(size_bytes), 0) from stored_files where deleted_at is null) as storage_bytes
                """, args);
        Map<UUID, Long> api = apiUsage.lastDays(30);
        UsageTotals usage = new UsageTotals(l(t, "customers"), l(t, "projects"), l(t, "work_orders"), l(t, "files"),
                l(t, "storage_bytes"), api == null ? null : api.values().stream().mapToLong(Long::longValue).sum());

        return new Dashboard(now, companies, users, usage, payments(args), activity(12));
    }

    /** SaaS subscription payments only (the platform's own revenue), never tenant transactions. */
    private Payments payments(MapSqlParameterSource args) {
        Map<String, Object> p = jdbc.queryForMap("""
                select count(*) filter (where status = 'PENDING') as pending,
                       count(*) filter (where status = 'APPROVED') as approved,
                       count(*) filter (where status = 'REJECTED') as rejected
                from subscription_requests""", args);
        List<Revenue> revenue = jdbc.query("""
                select currency, sum(amount) as total, coalesce(sum(amount) filter (where reviewed_at >= :since), 0) as recent,
                       count(*) as payments
                from subscription_requests where status = 'APPROVED' group by currency order by currency""", args,
                (rs, i) -> new Revenue(rs.getString("currency"), rs.getBigDecimal("total"), rs.getBigDecimal("recent"),
                        rs.getLong("payments")));
        return new Payments(l(p, "pending"), l(p, "approved"), l(p, "rejected"), revenue);
    }

    // ------------------------------------------------------------------ companies

    @Transactional(readOnly = true)
    public PageResponse<CompanyRow> companies(CompanyFilter filter, Integer page, Integer size, List<String> sort) {
        Pageable pageable = Pagination.pageable(page, size, sort, COMPANY_SORT,
                Sort.by(Sort.Direction.DESC, "co.created_at"));
        Instant now = Instant.now(clock);
        MapSqlParameterSource args = base(now);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        String like = Pagination.likePattern(filter.q());
        if (like != null) {
            where.append(" and (app_search_norm(co.name) like :q or lower(co.slug) like :q)");
            args.addValue("q", like);
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            String status = filter.status().trim().toUpperCase(java.util.Locale.ROOT);
            if (!STATUS_FILTERS.contains(status)) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Unknown company status");
            }
            switch (status) {
                case "DELETED" -> where.append(" and co.deleted_at is not null");
                case "OPERATIONAL" -> where.append(" and co.deleted_at is null and co.status in ('TRIAL', 'ACTIVE', 'GRACE')");
                default -> {
                    where.append(" and co.deleted_at is null and co.status = :status");
                    args.addValue("status", status);
                }
            }
        }
        if (filter.endingWithinDays() != null) {
            if (filter.endingWithinDays() < 0 || filter.endingWithinDays() > 366) {
                throw ApiException.badRequest("VALIDATION_ERROR", "endingWithinDays must be between 0 and 366");
            }
            where.append(" and co.deleted_at is null and co.current_period_end < :ending");
            args.addValue("ending", Timestamp.from(now.plus(Duration.ofDays(filter.endingWithinDays()))));
        }
        String from = """
                from co
                left join usr on usr.workspace_id = co.id
                left join cus on cus.workspace_id = co.id
                left join prj on prj.workspace_id = co.id
                left join job on job.workspace_id = co.id
                left join sto on sto.workspace_id = co.id
                """;
        Long total = jdbc.queryForObject(USAGE_CTES + "select count(*) " + from + where, args, Long.class);
        String order = pageable.getSort().stream()
                .map(o -> o.getProperty() + (o.isAscending() ? " asc" : " desc") + " nulls last")
                .collect(Collectors.joining(", "));
        args.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<CompanyRow> rows = jdbc.query(USAGE_CTES + COMPANY_COLUMNS + where + " order by " + order
                + ", co.id limit :limit offset :offset", args, (rs, i) -> company(rs));
        return PageResponse.of(new PageImpl<>(withApiUsage(rows), pageable, total == null ? 0 : total));
    }

    @Transactional(readOnly = true)
    public CompanyRow company(UUID id) {
        List<CompanyRow> rows = jdbc.query(USAGE_CTES + COMPANY_COLUMNS + " where co.id = :id",
                base(Instant.now(clock)).addValue("id", id), (rs, i) -> company(rs));
        if (rows.isEmpty()) {
            throw ApiException.notFound("Workspace");
        }
        return withApiUsage(rows).get(0);
    }

    // ------------------------------------------------------------------ usage

    @Transactional(readOnly = true)
    public UsageReport usage(String q, Integer page, Integer size, List<String> sort) {
        Instant now = Instant.now(clock);
        Map<String, Object> t = jdbc.queryForMap(USAGE_CTES + """
                select coalesce(sum(usr.members), 0) as members, coalesce(sum(usr.active_users), 0) as users,
                       coalesce(sum(cus.n), 0) as customers,
                       coalesce(sum(prj.n), 0) as projects, coalesce(sum(job.n), 0) as work_orders,
                       coalesce(sum(job.month), 0) as work_orders_month, coalesce(sum(sto.files), 0) as files,
                       coalesce(sum(sto.bytes), 0) as storage_bytes
                from co
                left join usr on usr.workspace_id = co.id
                left join cus on cus.workspace_id = co.id
                left join prj on prj.workspace_id = co.id
                left join job on job.workspace_id = co.id
                left join sto on sto.workspace_id = co.id
                where co.deleted_at is null""", base(now));
        Map<UUID, Long> api = apiUsage.lastDays(30);
        Usage totals = new Usage(l(t, "members"), l(t, "users"), l(t, "customers"), l(t, "projects"),
                l(t, "work_orders"), l(t, "work_orders_month"), l(t, "files"), l(t, "storage_bytes"),
                api == null ? null : api.values().stream().mapToLong(Long::longValue).sum());
        PageResponse<CompanyRow> rows = companies(new CompanyFilter(q, null, null), page, size,
                sort == null || sort.isEmpty() ? List.of("storage,desc") : sort);
        List<Metric> metrics = List.of(
                new Metric("users", true, "Active members of the company (memberships)"),
                new Metric("customers", true, "Customer records, count only"),
                new Metric("projects", true, "Projects not deleted, count only"),
                new Metric("workOrders", true, "Work orders not deleted, count only"),
                new Metric("storage", true, "Sum of stored file sizes recorded at upload (no bucket scan)"),
                new Metric("apiRequests", api != null,
                        "Authenticated API requests in the last 30 days (counted since this feature was installed)"),
                new Metric("bandwidth", false, "Not measured: downloads go directly to object storage"),
                new Metric("databaseSize", false, "Not measured per company: tables are shared"));
        return new UsageReport(now, totals, metrics, rows);
    }

    // ------------------------------------------------------------------ activity and audit

    /**
     * Platform-level events only: company sign-ups, subscription changes and platform-admin actions.
     * Tenant activity (what users do inside a workspace) is never included, and details/notes are left out.
     */
    @Transactional(readOnly = true)
    public List<Activity> activity(int limit) {
        int n = Math.min(Math.max(limit, 1), 100);
        return jdbc.query("""
                select * from (
                    (select 'COMPANY_REGISTERED' as type, null::varchar as status_from, null::varchar as status_to,
                            'TENANT' as actor_kind, null::varchar as actor_email, w.id as company_id,
                            w.name as company_name, 'WORKSPACE' as entity_type, w.id as entity_id,
                            w.created_at as occurred_at
                     from workspaces w order by w.created_at desc limit :n)
                    union all
                    (select 'SUBSCRIPTION_' || e.type, e.from_status, e.to_status, e.actor_kind, u.email, w.id, w.name,
                            'SUBSCRIPTION', e.subscription_id, e.occurred_at
                     from subscription_events e join workspaces w on w.id = e.workspace_id
                     left join users u on u.id = e.actor_user_id and e.actor_kind = 'ADMIN'
                     where e.type <> 'TRIAL_STARTED'
                     order by e.occurred_at desc limit :n)
                    union all
                    (select a.action, null, null, case when a.actor_user_id is null then 'SYSTEM' else 'ADMIN' end,
                            u.email, null, null, a.entity_type, a.entity_id, a.occurred_at
                     from audit_entries a left join users u on u.id = a.actor_user_id
                     -- subscription actions are already listed above (with the company) from subscription_events
                     where a.workspace_id is null and a.entity_type not in ('SUBSCRIPTION', 'SUBSCRIPTION_REQUEST')
                     order by a.occurred_at desc limit :n)
                ) x order by occurred_at desc limit :n""", Map.of("n", n),
                (rs, i) -> new Activity(rs.getString("type"), rs.getString("status_from"), rs.getString("status_to"),
                        rs.getString("actor_kind"), rs.getString("actor_email"), rs.getObject("company_id", UUID.class),
                        rs.getString("company_name"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
                        instant(rs, "occurred_at")));
    }

    /** The platform audit log (entries without a workspace). Entries are append-only in the database. */
    @Transactional(readOnly = true)
    public PageResponse<AuditRow> auditLog(AuditFilter filter, Integer page, Integer size, List<String> sort) {
        Pageable pageable = Pagination.pageable(page, size, sort, AUDIT_SORT,
                Sort.by(Sort.Direction.DESC, "a.occurred_at"));
        StringBuilder where = new StringBuilder(" where a.workspace_id is null");
        MapSqlParameterSource args = new MapSqlParameterSource();
        if (filter.action() != null && !filter.action().isBlank()) {
            where.append(" and a.action = :action");
            args.addValue("action", filter.action().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (filter.entityType() != null && !filter.entityType().isBlank()) {
            where.append(" and a.entity_type = :entityType");
            args.addValue("entityType", filter.entityType().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (filter.entityId() != null) {
            where.append(" and a.entity_id = :entityId");
            args.addValue("entityId", filter.entityId());
        }
        if (filter.actorUserId() != null) {
            where.append(" and a.actor_user_id = :actor");
            args.addValue("actor", filter.actorUserId());
        }
        if (filter.from() != null) {
            where.append(" and a.occurred_at >= :from");
            args.addValue("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" and a.occurred_at < :to");
            args.addValue("to", Timestamp.from(filter.to()));
        }
        Long total = jdbc.queryForObject("select count(*) from audit_entries a" + where, args, Long.class);
        String order = pageable.getSort().stream().map(o -> o.getProperty() + (o.isAscending() ? " asc" : " desc"))
                .collect(Collectors.joining(", "));
        args.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<AuditRow> rows = jdbc.query("""
                select a.id, a.action, a.entity_type, a.entity_id, a.actor_user_id, a.actor_label, u.email as actor_email,
                       a.changes::text as changes, a.occurred_at
                from audit_entries a left join users u on u.id = a.actor_user_id""" + where + " order by " + order
                + ", a.id limit :limit offset :offset", args,
                (rs, i) -> new AuditRow(rs.getObject("id", UUID.class), rs.getString("action"),
                        rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
                        rs.getObject("actor_user_id", UUID.class), rs.getString("actor_label"),
                        rs.getString("actor_email"), readJson(rs.getString("changes")), instant(rs, "occurred_at")));
        return PageResponse.of(new PageImpl<>(rows, pageable, total == null ? 0 : total));
    }

    /** Distinct platform audit actions, for the filter list. */
    @Transactional(readOnly = true)
    public List<String> auditActions() {
        return jdbc.queryForList("select distinct action from audit_entries where workspace_id is null order by action",
                Map.of(), String.class);
    }

    // ------------------------------------------------------------------ helpers

    private MapSqlParameterSource base(Instant now) {
        Instant monthStart = now.atOffset(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        return new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("grace", graceDays)
                .addValue("enforcement", enforcement).addValue("monthStart", Timestamp.from(monthStart));
    }

    private List<CompanyRow> withApiUsage(List<CompanyRow> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<UUID, Long> api = apiUsage.lastDays(30);
        List<CompanyRow> out = new ArrayList<>(rows.size());
        for (CompanyRow r : rows) {
            Usage u = r.usage();
            Long requests = api == null ? null : api.getOrDefault(r.id(), 0L);
            out.add(new CompanyRow(r.id(), r.name(), r.slug(), r.createdAt(), r.deletedAt(), r.status(), r.planCode(),
                    r.planNameEn(), r.planNameAr(), r.billingPeriod(), r.currentPeriodEnd(), r.ownerEmail(),
                    new Usage(u.members(), u.activeUsers(), u.customers(), u.projects(), u.workOrders(),
                            u.workOrdersThisMonth(), u.files(), u.storageBytes(), requests)));
        }
        return out;
    }

    private static CompanyRow company(ResultSet rs) throws SQLException {
        return new CompanyRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"),
                instant(rs, "created_at"), instant(rs, "deleted_at"), rs.getString("status"), rs.getString("plan_code"),
                rs.getString("plan_name_en"), rs.getString("plan_name_ar"), rs.getString("billing_period"),
                instant(rs, "current_period_end"), rs.getString("owner_email"),
                new Usage(rs.getLong("members"), rs.getLong("active_users"), rs.getLong("customers"),
                        rs.getLong("projects"), rs.getLong("work_orders"), rs.getLong("work_orders_month"),
                        rs.getLong("files"), rs.getLong("storage_bytes"), null));
    }

    private JsonNode readJson(String text) {
        if (text == null) {
            return null;
        }
        try {
            return json.readTree(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static long l(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? 0 : ((Number) v).longValue();
    }

    // ------------------------------------------------------------------ views

    public record CompanyFilter(String q, String status, Integer endingWithinDays) {
    }

    public record AuditFilter(String action, String entityType, UUID entityId, UUID actorUserId, Instant from,
            Instant to) {
    }

    /** Counts only. {@code apiRequests30d} is null when the counter store cannot be read. */
    public record Usage(long members, long activeUsers, long customers, long projects, long workOrders,
            long workOrdersThisMonth, long files, long storageBytes, Long apiRequests30d) {
    }

    /** A company (workspace) with its administrative metadata and aggregate usage. */
    public record CompanyRow(UUID id, String name, String slug, Instant createdAt, Instant deletedAt, String status,
            String planCode, String planNameEn, String planNameAr, String billingPeriod, Instant currentPeriodEnd,
            String ownerEmail, Usage usage) {
    }

    public record CompanyCounts(long total, long operational, long trial, long paid, long grace, long expired,
            long suspended, long deleted, long newLast30Days, long endingWithin7Days) {
    }

    public record UserCounts(long total, long active, long inactive, long platformAdmins, long newLast30Days,
            long signedInLast30Days) {
    }

    public record UsageTotals(long customers, long projects, long workOrders, long files, long storageBytes,
            Long apiRequests30d) {
    }

    public record Revenue(String currency, BigDecimal total, BigDecimal last30Days, long payments) {
    }

    public record Payments(long pending, long approved, long rejected, List<Revenue> revenue) {
    }

    public record Activity(String type, String fromStatus, String toStatus, String actorKind, String actorEmail,
            UUID companyId, String companyName, String entityType, UUID entityId, Instant occurredAt) {
    }

    public record Dashboard(Instant generatedAt, CompanyCounts companies, UserCounts users, UsageTotals usage,
            Payments payments, List<Activity> recentActivity) {
    }

    public record Metric(String key, boolean available, String description) {
    }

    public record UsageReport(Instant generatedAt, Usage totals, List<Metric> metrics, PageResponse<CompanyRow> companies) {
    }

    public record AuditRow(UUID id, String action, String entityType, UUID entityId, UUID actorUserId,
            String actorLabel, String actorEmail, JsonNode changes, Instant occurredAt) {
    }
}

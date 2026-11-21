package com.crmplatform.platform.web;

import com.crmplatform.platform.application.PlatformHealth;
import com.crmplatform.platform.application.PlatformInsights;
import com.crmplatform.platform.application.PlatformInsights.Activity;
import com.crmplatform.platform.application.PlatformInsights.AuditFilter;
import com.crmplatform.platform.application.PlatformInsights.AuditRow;
import com.crmplatform.platform.application.PlatformInsights.CompanyFilter;
import com.crmplatform.platform.application.PlatformInsights.CompanyRow;
import com.crmplatform.platform.application.PlatformInsights.Dashboard;
import com.crmplatform.platform.application.PlatformInsights.UsageReport;
import com.crmplatform.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform admin console (read side): dashboard, companies, usage, health, activity and the
 * platform audit log. Write actions live with the module that owns the data (billing for
 * subscriptions and plans, identity for accounts). Aggregates only — no tenant business records.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Platform admin — console")
@PreAuthorize("hasAuthority('platform:admin')")
public class PlatformConsoleController {

    private final PlatformInsights insights;
    private final PlatformHealth health;

    public PlatformConsoleController(PlatformInsights insights, PlatformHealth health) {
        this.insights = insights;
        this.health = health;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return insights.dashboard();
    }

    @GetMapping("/companies")
    public PageResponse<CompanyRow> companies(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer endingWithinDays,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        return insights.companies(new CompanyFilter(q, status, endingWithinDays), page, size, sort);
    }

    @GetMapping("/companies/{id}")
    public CompanyRow company(@PathVariable UUID id) {
        return insights.company(id);
    }

    @GetMapping("/usage")
    public UsageReport usage(@RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        return insights.usage(q, page, size, sort);
    }

    @GetMapping("/activity")
    public List<Activity> activity(@RequestParam(defaultValue = "30") int limit) {
        return insights.activity(limit);
    }

    @GetMapping("/health")
    public PlatformHealth.Report health() {
        return health.check();
    }

    @GetMapping("/audit-log")
    public PageResponse<AuditRow> auditLog(@RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType, @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        return insights.auditLog(new AuditFilter(action, entityType, entityId, actorUserId, from, to), page, size, sort);
    }

    @GetMapping("/audit-log/actions")
    public List<String> auditActions() {
        return insights.auditActions();
    }
}

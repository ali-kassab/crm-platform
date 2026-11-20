package com.crmplatform.reporting.web;

import com.crmplatform.reporting.application.DashboardService;
import com.crmplatform.reporting.application.VisitReportPdf;
import com.crmplatform.shared.i18n.Languages;
import com.crmplatform.shared.ratelimit.RateLimiter;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.security.Permissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.i18n.LocaleContextHolder;
import com.crmplatform.reporting.application.DashboardService.Period;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reports and dashboards (R12). Read-only; available in read-only (expired) workspaces too. */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
public class ReportsController {

    /** PDF rendering is CPU-heavy: per user, per minute. */
    static final int PDF_PER_MINUTE = 30;

    private final VisitReportPdf visitReports;
    private final DashboardService dashboards;
    private final RateLimiter rateLimiter;

    public ReportsController(VisitReportPdf visitReports, DashboardService dashboards, RateLimiter rateLimiter) {
        this.visitReports = visitReports;
        this.dashboards = dashboards;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping(value = "/visits/{visitId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    @Operation(summary = "Technical visit report as PDF (Arabic RTL or English); technicians: own visits only")
    public ResponseEntity<byte[]> visitReport(@PathVariable UUID visitId,
            @RequestParam(required = false) String lang) {
        AuthenticatedUser user = CurrentUser.require();
        rateLimiter.check("pdf:" + user.userId(), PDF_PER_MINUTE, Duration.ofMinutes(1));
        String language = Languages.normalize(lang)
                .orElseGet(() -> Languages.normalize(LocaleContextHolder.getLocale().toLanguageTag()).orElse(null));
        VisitReportPdf.Pdf pdf = visitReports.render(visitId, language);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore()) // customer data
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('reports:read')")
    @Operation(summary = "Dashboard counts for a period (default MONTH) and, with finance:read, money")
    public DashboardResponse dashboard(@RequestParam(defaultValue = "MONTH") Period period) {
        boolean finance = CurrentUser.require().has(Permissions.FINANCE_READ);
        return DashboardResponse.of(dashboards.of(period, finance));
    }

    /** {@code from} is null for ALL; {@code finance} is null without finance:read. */
    public record DashboardResponse(Period period, LocalDate from, LocalDate to, String timeZone, Jobs jobs,
            Visits visits, Maintenance maintenance, Finance finance) {

        static DashboardResponse of(DashboardService.Dashboard d) {
            var o = d.operations();
            long open = o.openJobsByStatus().values().stream().mapToLong(Long::longValue).sum();
            return new DashboardResponse(d.period(), d.from(), d.to(), d.timeZone(),
                    new Jobs(open, o.openJobsByStatus(), o.slaBreachedOpen(), o.jobsCreated(), o.jobsCompleted()),
                    new Visits(o.visitsToday(), o.visitsOverdue(), o.visitsCompleted(), o.visitsMissed()),
                    new Maintenance(o.maintenanceOverdue(), o.warrantiesExpiringSoon()),
                    d.finance() == null ? null : new Finance(money(d.finance().received()),
                            money(d.finance().paid()), money(d.finance().outstanding())));
        }
    }

    public record Jobs(long open, Map<String, Long> openByStatus, long slaBreached, long created, long completed) {
    }

    public record Visits(long today, long overdue, long completed, long missed) {
    }

    public record Maintenance(long overdue, long warrantiesExpiringSoon) {
    }

    /** Money as decimal strings, like every other money field of the API. */
    public record Finance(String received, String paid, String outstanding) {
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

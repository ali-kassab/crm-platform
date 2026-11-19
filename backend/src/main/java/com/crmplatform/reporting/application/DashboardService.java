package com.crmplatform.reporting.application;

import com.crmplatform.finance.Accounting;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.operations.OperationsOverview;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace dashboard (R12, API spec {@code GET /reports/dashboard?period=}): operational counts plus,
 * for finance readers, money for the period. Periods are the workspace's own calendar days (its time
 * zone), not the server's, and always end today.
 */
@Service
public class DashboardService {

    /** WEEK = the last 7 days; MONTH/QUARTER/YEAR = since the start of the current one; ALL = everything. */
    public enum Period { WEEK, MONTH, QUARTER, YEAR, ALL }

    private static final LocalDate BEGINNING = LocalDate.of(2000, 1, 1);

    private final OperationsOverview operations;
    private final Accounting accounting;
    private final WorkspaceMembers members;
    private final Clock clock;

    public DashboardService(OperationsOverview operations, Accounting accounting, WorkspaceMembers members,
            Clock clock) {
        this.operations = operations;
        this.accounting = accounting;
        this.members = members;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard of(Period period, boolean includeFinance) {
        ZoneId zone = zone(members.currentWorkspace().timeZone());
        Instant now = Instant.now(clock);
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate first = switch (period) {
            case WEEK -> today.minusDays(6);
            case MONTH -> today.withDayOfMonth(1);
            case QUARTER -> today.with(IsoFields.DAY_OF_QUARTER, 1);
            case YEAR -> today.withDayOfYear(1);
            case ALL -> BEGINNING;
        };
        LocalDate end = today.plusDays(1); // exclusive
        OperationsOverview.Snapshot ops = operations.snapshot(now, first.atStartOfDay(zone).toInstant(),
                end.atStartOfDay(zone).toInstant(), today.atStartOfDay(zone).toInstant(),
                end.atStartOfDay(zone).toInstant(), today);
        Accounting.PeriodSummary money = includeFinance ? accounting.periodSummary(first, end) : null;
        return new Dashboard(period, period == Period.ALL ? null : first, today, zone.getId(), ops, money);
    }

    private static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException e) {
            return ZoneId.of("Africa/Cairo");
        }
    }

    /** {@code from} is null for ALL. */
    public record Dashboard(Period period, LocalDate from, LocalDate to, String timeZone,
            OperationsOverview.Snapshot operations, Accounting.PeriodSummary finance) {
    }
}

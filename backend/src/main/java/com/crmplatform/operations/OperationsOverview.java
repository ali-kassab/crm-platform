package com.crmplatform.operations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** Workspace-wide operational counts for the dashboard (R12). Counts only — no business data. */
public interface OperationsOverview {

    /**
     * @param from    start of the reporting period (inclusive)
     * @param to      end of the reporting period (exclusive)
     * @param dayFrom start of "today" in the workspace time zone
     * @param dayTo   end of "today" in the workspace time zone
     * @param today   the workspace's local date (maintenance and warranty dates are dates)
     */
    Snapshot snapshot(Instant now, Instant from, Instant to, Instant dayFrom, Instant dayTo, LocalDate today);

    /**
     * @param openJobsByStatus      NEW, SCHEDULED, IN_PROGRESS, ON_HOLD → count (zeros included)
     * @param slaBreachedOpen       open jobs past their response deadline without a timely start
     * @param visitsOverdue         still SCHEDULED although their window has ended
     * @param warrantiesExpiringSoon ACTIVE warranties ending within {@link #EXPIRY_WINDOW_DAYS} days
     */
    record Snapshot(Map<String, Long> openJobsByStatus, long slaBreachedOpen, long jobsCreated, long jobsCompleted,
            long visitsToday, long visitsOverdue, long visitsCompleted, long visitsMissed, long maintenanceOverdue,
            long warrantiesExpiringSoon) {
    }

    int EXPIRY_WINDOW_DAYS = 30;
}

package com.crmplatform.operations.maintenance.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One due date of a plan and the job generated for it; UNIQUE(plan, due date) keeps generation idempotent. */
@Entity
@Table(name = "maintenance_occurrences")
public class MaintenanceOccurrence extends TenantEntity {

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "overdue_notified_at")
    private Instant overdueNotifiedAt;

    protected MaintenanceOccurrence() {
    }

    public MaintenanceOccurrence(UUID planId, LocalDate dueDate, UUID jobId) {
        this.planId = planId;
        this.dueDate = dueDate;
        this.jobId = jobId;
    }

    public void overdueNotified(Instant at) {
        this.overdueNotifiedAt = at;
    }

    public UUID getPlanId() {
        return planId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public UUID getJobId() {
        return jobId;
    }

    public Instant getOverdueNotifiedAt() {
        return overdueNotifiedAt;
    }
}

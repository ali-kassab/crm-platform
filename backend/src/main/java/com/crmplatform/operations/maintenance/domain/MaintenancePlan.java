package com.crmplatform.operations.maintenance.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

/**
 * A preventive-maintenance plan for one customer site — also the maintenance agreement (D3): its
 * period, included visits and response-time SLA. Due dates are always computed from the start date
 * (no month-end drift); each due date generates at most one job.
 */
@Entity
@Table(name = "maintenance_plans")
@SQLRestriction("deleted_at is null")
public class MaintenancePlan extends TenantEntity {

    public enum Frequency {
        MONTHLY(1), QUARTERLY(3), SEMI_ANNUAL(6), ANNUAL(12), CUSTOM(0);

        final int months;

        Frequency(int months) {
            this.months = months;
        }
    }

    public enum Status { ACTIVE, PAUSED, ENDED }

    @Column(name = "contact_id", nullable = false, updatable = false)
    private UUID contactId;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Frequency frequency;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "lead_days", nullable = false)
    private int leadDays = 7;

    @Column(name = "response_hours")
    private Integer responseHours;

    @Column(name = "included_visits")
    private Integer includedVisits;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount;

    @Column(name = "visit_budget", nullable = false, precision = 19, scale = 2)
    private BigDecimal visitBudget = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status = Status.ACTIVE;

    @Column(name = "renewal_reminder_sent_at")
    private Instant renewalReminderSentAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "maintenance_plan_assignees", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "user_id")
    private Set<UUID> assigneeUserIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "maintenance_plan_equipment", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "equipment_id")
    private Set<UUID> equipmentIds = new HashSet<>();

    protected MaintenancePlan() {
    }

    public MaintenancePlan(UUID contactId, UUID locationId, String title) {
        this.contactId = contactId;
        this.locationId = locationId;
        setTitle(title);
    }

    // ------------------------------------------------------------------ schedule

    /** Sets the cadence and recomputes the next due date: the first one on or after {@code from}. */
    public void schedule(Frequency frequency, Integer intervalDays, LocalDate startDate, LocalDate endDate,
            LocalDate from) {
        this.frequency = frequency;
        this.intervalDays = frequency == Frequency.CUSTOM ? intervalDays : null;
        if (!startDate.equals(this.startDate) || !java.util.Objects.equals(endDate, this.endDate)) {
            this.renewalReminderSentAt = null;
        }
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextDueDate = bounded(firstOnOrAfter(from.isBefore(startDate) ? startDate : from));
    }

    /** The n-th due date (n = 0 is the start date). */
    LocalDate occurrence(long n) {
        return frequency == Frequency.CUSTOM ? startDate.plusDays(n * intervalDays)
                : startDate.plusMonths(n * frequency.months);
    }

    LocalDate firstOnOrAfter(LocalDate day) {
        if (!day.isAfter(startDate)) {
            return startDate;
        }
        long n = frequency == Frequency.CUSTOM ? ChronoUnit.DAYS.between(startDate, day) / intervalDays
                : ChronoUnit.MONTHS.between(startDate, day) / frequency.months;
        LocalDate d = occurrence(n);
        while (d.isBefore(day)) {
            d = occurrence(++n);
        }
        return d;
    }

    /** Moves past the current due date (after generating it, or when it was already generated). */
    public void advance() {
        if (nextDueDate != null) {
            nextDueDate = bounded(firstOnOrAfter(nextDueDate.plusDays(1)));
        }
    }

    public void countGenerated() {
        generatedCount++;
    }

    /** No due date past the agreement's end or beyond its included visits. */
    private LocalDate bounded(LocalDate due) {
        if (endDate != null && due.isAfter(endDate)) {
            return null;
        }
        if (includedVisits != null && generatedCount >= includedVisits) {
            return null;
        }
        return due;
    }

    /** Ready to generate: active, has a due date and is inside the lead window. */
    public boolean isDue(LocalDate today) {
        return status == Status.ACTIVE && nextDueDate != null && !nextDueDate.minusDays(leadDays).isAfter(today);
    }

    // ------------------------------------------------------------------ setters

    public void setTitle(String title) {
        this.title = title.trim();
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLeadDays(int leadDays) {
        this.leadDays = leadDays;
    }

    public void setResponseHours(Integer responseHours) {
        this.responseHours = responseHours;
    }

    public void setIncludedVisits(Integer includedVisits) {
        this.includedVisits = includedVisits;
        if (nextDueDate != null) {
            nextDueDate = bounded(nextDueDate);
        }
    }

    public void setVisitBudget(BigDecimal visitBudget) {
        this.visitBudget = visitBudget == null ? BigDecimal.ZERO : visitBudget;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void renewalReminded(Instant at) {
        this.renewalReminderSentAt = at;
    }

    public void assign(Set<UUID> userIds) {
        assigneeUserIds.clear();
        assigneeUserIds.addAll(userIds);
    }

    public void replaceEquipment(Set<UUID> ids) {
        equipmentIds.clear();
        equipmentIds.addAll(ids);
    }

    // ------------------------------------------------------------------ getters

    public UUID getContactId() {
        return contactId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public Integer getIntervalDays() {
        return intervalDays;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public int getLeadDays() {
        return leadDays;
    }

    public Integer getResponseHours() {
        return responseHours;
    }

    public Integer getIncludedVisits() {
        return includedVisits;
    }

    public int getGeneratedCount() {
        return generatedCount;
    }

    public BigDecimal getVisitBudget() {
        return visitBudget;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRenewalReminderSentAt() {
        return renewalReminderSentAt;
    }

    public Set<UUID> getAssigneeUserIds() {
        return Set.copyOf(assigneeUserIds);
    }

    public Set<UUID> getEquipmentIds() {
        return Set.copyOf(equipmentIds);
    }
}

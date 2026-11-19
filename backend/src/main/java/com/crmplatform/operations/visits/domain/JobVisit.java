package com.crmplatform.operations.visits.domain;

import com.crmplatform.shared.error.ApiException;
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
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

/**
 * A scheduled visit to the job's site (D1). A job can need several visits (multi-day installs,
 * follow-ups); rescheduling closes this visit as {@code RESCHEDULED} and links the new one, so the
 * visit history stays complete.
 */
@Entity
@Table(name = "job_visits")
@SQLRestriction("deleted_at is null")
public class JobVisit extends TenantEntity {

    public enum Status { SCHEDULED, IN_PROGRESS, COMPLETED, MISSED, CANCELLED, RESCHEDULED }

    public enum Outcome { RESOLVED, FOLLOW_UP_REQUIRED, NOT_RESOLVED, CUSTOMER_UNAVAILABLE }

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.SCHEDULED;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "actual_end_at")
    private Instant actualEndAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Outcome outcome;

    @Column(length = 2000)
    private String notes;

    @Column(name = "rescheduled_from_id", updatable = false)
    private UUID rescheduledFromId;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "visit_assignees", joinColumns = @JoinColumn(name = "visit_id"))
    @Column(name = "user_id")
    private Set<UUID> assigneeUserIds = new HashSet<>();

    protected JobVisit() {
    }

    public JobVisit(UUID jobId, Instant start, Instant end, Set<UUID> assignees, String notes, UUID rescheduledFromId) {
        this.jobId = jobId;
        setWindow(start, end);
        this.assigneeUserIds.addAll(assignees);
        this.notes = notes;
        this.rescheduledFromId = rescheduledFromId;
    }

    private void setWindow(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "scheduledEnd must be after scheduledStart");
        }
        this.scheduledStart = start;
        this.scheduledEnd = end;
    }

    public void start(Instant now) {
        require(status == Status.SCHEDULED || status == Status.MISSED, "start");
        status = Status.IN_PROGRESS;
        actualStartAt = now;
    }

    /** Completing straight from SCHEDULED is allowed (the technician forgot to press start). */
    public void complete(Outcome result, String completionNotes, Instant now) {
        require(status == Status.IN_PROGRESS || status == Status.SCHEDULED || status == Status.MISSED, "complete");
        if (actualStartAt == null) {
            actualStartAt = now;
        }
        actualEndAt = now;
        outcome = result;
        if (completionNotes != null && !completionNotes.isBlank()) {
            notes = completionNotes;
        }
        status = Status.COMPLETED;
    }

    public void cancel(String reason) {
        require(status == Status.SCHEDULED || status == Status.MISSED, "cancel");
        status = Status.CANCELLED;
        if (reason != null && !reason.isBlank()) {
            notes = reason;
        }
    }

    public void markRescheduled() {
        require(status == Status.SCHEDULED || status == Status.MISSED, "reschedule");
        status = Status.RESCHEDULED;
    }

    private void require(boolean allowed, String action) {
        if (!allowed) {
            throw ApiException.conflict("INVALID_VISIT_TRANSITION", "A " + status + " visit cannot " + action);
        }
    }

    public boolean isAssignedTo(UUID userId) {
        return assigneeUserIds.contains(userId);
    }

    public UUID getJobId() {
        return jobId;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public Instant getScheduledEnd() {
        return scheduledEnd;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getActualStartAt() {
        return actualStartAt;
    }

    public Instant getActualEndAt() {
        return actualEndAt;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getRescheduledFromId() {
        return rescheduledFromId;
    }

    public Set<UUID> getAssigneeUserIds() {
        return Set.copyOf(assigneeUserIds);
    }
}

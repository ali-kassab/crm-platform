package com.crmplatform.operations.visits.application;

import com.crmplatform.operations.jobs.application.JobAccess;
import com.crmplatform.operations.jobs.application.JobService;
import com.crmplatform.operations.jobs.domain.Job;
import com.crmplatform.operations.visits.domain.JobVisit;
import com.crmplatform.operations.visits.domain.JobVisitRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visit scheduling and execution (directive §5D, D1). Works for an independent technician (no team:
 * the visit is assigned to themselves) as well as for dispatchers assigning technicians.
 */
@Service
public class VisitService {

    /** A scheduled visit is considered missed this long after its window ended without being started. */
    static final Duration MISSED_GRACE = Duration.ofHours(2);

    private final JobVisitRepository visits;
    private final JobService jobs;
    private final AuditTrail audit;
    private final Clock clock;

    public VisitService(JobVisitRepository visits, JobService jobs, AuditTrail audit, Clock clock) {
        this.visits = visits;
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Page<JobVisit> search(VisitFilter filter, Pageable pageable) {
        VisitFilter f = JobAccess.limitedToAssigned() ? filter.withAssignee(JobAccess.currentUser()) : filter;
        return visits.findAll(matching(f, Instant.now(clock)), pageable);
    }

    @Transactional(readOnly = true)
    public List<JobVisit> ofJob(UUID jobId) {
        jobs.get(jobId); // access check
        return visits.findByJobIdOrderByScheduledStartAsc(jobId);
    }

    /** 404 when the caller may not see the visit (technicians: assigned to them or to their job). */
    @Transactional(readOnly = true)
    public JobVisit get(UUID id) {
        JobVisit visit = visits.findById(id).orElseThrow(() -> ApiException.notFound("Visit"));
        if (JobAccess.limitedToAssigned() && !visit.isAssignedTo(JobAccess.currentUser())) {
            jobs.get(visit.getJobId()); // throws 404 unless the job is theirs
        }
        return visit;
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public JobVisit schedule(UUID jobId, Instant start, Instant end, Collection<UUID> assigneeUserIds, String notes) {
        Job job = jobs.get(jobId);
        if (!job.getStatus().isOpen()) {
            throw ApiException.conflict("JOB_CLOSED", "This job is closed; reopen it to schedule a visit");
        }
        Set<UUID> assignees = assignees(job, assigneeUserIds);
        jobs.ensureAssigned(jobId, assignees);
        JobVisit saved = visits.save(new JobVisit(jobId, start, end, assignees, notes, null));
        jobs.advanceForVisit(jobId, Job.Status.SCHEDULED);
        audit.created("VISIT", saved.getId(), job.getProjectId(), jobId,
                Map.of("scheduledStart", start.toString(), "scheduledEnd", end.toString()));
        return saved;
    }

    @Transactional
    public JobVisit start(UUID id) {
        JobVisit visit = get(id);
        visit.start(Instant.now(clock));
        jobs.advanceForVisit(visit.getJobId(), Job.Status.IN_PROGRESS);
        audit.action("START", "VISIT", id, null, visit.getJobId(), Map.of());
        return visits.saveAndFlush(visit);
    }

    /** Records the outcome; optionally completes the job too (single-visit jobs). */
    @Transactional
    public JobVisit complete(UUID id, String outcome, String notes, boolean completeJob) {
        JobVisit visit = get(id);
        visit.complete(parseOutcome(outcome), notes, Instant.now(clock));
        jobs.advanceForVisit(visit.getJobId(), Job.Status.IN_PROGRESS);
        if (completeJob) {
            jobs.advanceForVisit(visit.getJobId(), Job.Status.COMPLETED);
        }
        audit.action("COMPLETE", "VISIT", id, null, visit.getJobId(), Map.of("outcome", visit.getOutcome().name()));
        return visits.saveAndFlush(visit);
    }

    @Transactional
    public JobVisit cancel(UUID id, String reason) {
        JobVisit visit = get(id);
        visit.cancel(reason);
        audit.action("CANCEL", "VISIT", id, null, visit.getJobId(), reason == null ? Map.of() : Map.of("reason", reason));
        return visits.saveAndFlush(visit);
    }

    /** Closes the visit as RESCHEDULED and creates the linked replacement visit. */
    @Transactional
    public JobVisit reschedule(UUID id, Instant start, Instant end, Collection<UUID> assigneeUserIds) {
        JobVisit old = get(id);
        Job job = jobs.get(old.getJobId());
        old.markRescheduled();
        visits.saveAndFlush(old);
        Set<UUID> assignees = assigneeUserIds == null || assigneeUserIds.isEmpty() ? old.getAssigneeUserIds()
                : assignees(job, assigneeUserIds);
        jobs.ensureAssigned(job.getId(), assignees);
        JobVisit next = visits.save(new JobVisit(job.getId(), start, end, assignees, old.getNotes(), old.getId()));
        audit.action("RESCHEDULE", "VISIT", old.getId(), job.getProjectId(), job.getId(),
                Map.of("newVisitId", next.getId().toString(), "scheduledStart", start.toString()));
        return next;
    }

    /** Scheduler (platform scope): idempotent, only SCHEDULED visits past their window change. */
    @Transactional
    public int markMissed() {
        Instant now = Instant.now(clock);
        return visits.markMissedBefore(now.minus(MISSED_GRACE), now);
    }

    // ------------------------------------------------------------------ helpers

    /** Explicit assignees, else the job's technicians, else (one-person workspaces) the caller. */
    private Set<UUID> assignees(Job job, Collection<UUID> requested) {
        if (requested != null && !requested.isEmpty()) {
            return new LinkedHashSet<>(requested);
        }
        if (!job.getAssigneeUserIds().isEmpty()) {
            return job.getAssigneeUserIds();
        }
        return Set.of(JobAccess.currentUser());
    }

    static JobVisit.Outcome parseOutcome(String value) {
        try {
            return JobVisit.Outcome.valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "outcome must be RESOLVED, FOLLOW_UP_REQUIRED, NOT_RESOLVED or CUSTOMER_UNAVAILABLE");
        }
    }

    static JobVisit.Status parseStatus(String value) {
        try {
            return JobVisit.Status.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown visit status");
        }
    }

    private static Specification<JobVisit> matching(VisitFilter f, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.jobId() != null) {
                p.add(cb.equal(root.get("jobId"), f.jobId()));
            }
            if (f.assigneeUserId() != null) {
                p.add(cb.isMember(f.assigneeUserId(), root.<Collection<UUID>>get("assigneeUserIds")));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), parseStatus(f.status())));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("scheduledStart"), f.to()));
            }
            if (Boolean.TRUE.equals(f.overdue())) { // should have started already, not started yet
                p.add(cb.equal(root.get("status"), JobVisit.Status.SCHEDULED));
                p.add(cb.lessThan(root.get("scheduledStart"), now));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public record VisitFilter(UUID jobId, UUID assigneeUserId, String status, Instant from, Instant to,
            Boolean overdue) {

        VisitFilter withAssignee(UUID userId) {
            return new VisitFilter(jobId, userId, status, from, to, overdue);
        }
    }
}

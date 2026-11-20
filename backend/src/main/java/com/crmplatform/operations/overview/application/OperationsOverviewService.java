package com.crmplatform.operations.overview.application;

import com.crmplatform.operations.OperationsOverview;
import com.crmplatform.operations.jobs.domain.Job;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard counts. JPQL aggregates only: every entity here is a tenant entity, so Hibernate adds
 * the caller's workspace to each query (no native SQL, which would bypass that filter).
 */
@Service
public class OperationsOverviewService implements OperationsOverview {

    private static final String OPEN = "(com.crmplatform.operations.jobs.domain.Job.Status.NEW, "
            + "com.crmplatform.operations.jobs.domain.Job.Status.SCHEDULED, "
            + "com.crmplatform.operations.jobs.domain.Job.Status.IN_PROGRESS, "
            + "com.crmplatform.operations.jobs.domain.Job.Status.ON_HOLD)";
    private static final String VISIT = "com.crmplatform.operations.visits.domain.JobVisit.Status.";

    private final EntityManager em;

    public OperationsOverviewService(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public Snapshot snapshot(Instant now, Instant from, Instant to, Instant dayFrom, Instant dayTo, LocalDate today) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Job.Status s : Job.Status.values()) {
            if (s.isOpen()) {
                byStatus.put(s.name(), 0L);
            }
        }
        List<Object[]> rows = em.createQuery("select j.status, count(j) from Job j where j.status in " + OPEN
                + " group by j.status", Object[].class).getResultList();
        rows.forEach(r -> byStatus.put(((Job.Status) r[0]).name(), (Long) r[1]));

        long sla = count("select count(j) from Job j where j.status in " + OPEN
                + " and j.responseDueAt is not null and ((j.actualStartAt is null and j.responseDueAt < :now)"
                + " or j.actualStartAt > j.responseDueAt)", "now", now);
        long created = count("select count(j) from Job j where j.createdAt >= :from and j.createdAt < :to",
                "from", from, "to", to);
        long completed = count("select count(j) from Job j where j.status = "
                + "com.crmplatform.operations.jobs.domain.Job.Status.COMPLETED "
                + "and j.completedAt >= :from and j.completedAt < :to", "from", from, "to", to);
        long visitsToday = count("select count(v) from JobVisit v where v.scheduledStart >= :from "
                + "and v.scheduledStart < :to and v.status not in (" + VISIT + "CANCELLED, " + VISIT + "RESCHEDULED)",
                "from", dayFrom, "to", dayTo);
        long overdue = count("select count(v) from JobVisit v where v.status = " + VISIT + "SCHEDULED "
                + "and v.scheduledEnd < :now", "now", now);
        long visitsCompleted = count("select count(v) from JobVisit v where v.status = " + VISIT + "COMPLETED "
                + "and v.actualEndAt >= :from and v.actualEndAt < :to", "from", from, "to", to);
        long missed = count("select count(v) from JobVisit v where v.status = " + VISIT + "MISSED "
                + "and v.scheduledStart >= :from and v.scheduledStart < :to", "from", from, "to", to);
        long maintenance = count("select count(o) from MaintenanceOccurrence o, Job j where j.id = o.jobId "
                + "and o.dueDate < :today and j.status in " + OPEN, "today", today);
        long warranties = count("select count(w) from Warranty w where w.status = "
                + "com.crmplatform.operations.warranties.domain.Warranty.Status.ACTIVE "
                + "and w.endDate >= :today and w.endDate <= :until", "today", today, "until",
                today.plusDays(EXPIRY_WINDOW_DAYS));
        return new Snapshot(byStatus, sla, created, completed, visitsToday, overdue, visitsCompleted, missed,
                maintenance, warranties);
    }

    private long count(String jpql, Object... params) {
        TypedQuery<Long> q = em.createQuery(jpql, Long.class);
        for (int i = 0; i < params.length; i += 2) {
            q.setParameter((String) params[i], params[i + 1]);
        }
        return q.getSingleResult();
    }
}

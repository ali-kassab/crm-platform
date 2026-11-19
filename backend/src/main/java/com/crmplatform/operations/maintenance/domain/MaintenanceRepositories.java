package com.crmplatform.operations.maintenance.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Repositories of the maintenance feature. Native "platform scope" queries return [id, workspace_id]. */
public final class MaintenanceRepositories {

    private MaintenanceRepositories() {
    }

    public interface MaintenancePlanRepository
            extends JpaRepository<MaintenancePlan, UUID>, JpaSpecificationExecutor<MaintenancePlan> {

        @Query("select min(p.responseHours) from MaintenancePlan p where p.locationId = :locationId "
                + "and p.status = com.crmplatform.operations.maintenance.domain.MaintenancePlan.Status.ACTIVE "
                + "and p.responseHours is not null")
        Optional<Integer> shortestResponseHoursAt(UUID locationId);

        boolean existsByContactId(UUID contactId);

        boolean existsByLocationId(UUID locationId);

        @Query(value = "select p.id, p.workspace_id from maintenance_plans p where p.deleted_at is null "
                + "and p.status = 'ACTIVE' and p.next_due_date is not null "
                + "and p.next_due_date - p.lead_days <= :today", nativeQuery = true)
        List<Object[]> dueForGeneration(LocalDate today);

        @Query(value = "select p.id, p.workspace_id from maintenance_plans p where p.deleted_at is null "
                + "and p.status = 'ACTIVE' and p.end_date is not null and p.renewal_reminder_sent_at is null "
                + "and p.end_date >= :today and p.end_date - :days <= :today", nativeQuery = true)
        List<Object[]> endingWithin(LocalDate today, int days);

        /** Platform scope: agreements whose end date passed become ENDED (idempotent). */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("update MaintenancePlan p set p.status = com.crmplatform.operations.maintenance.domain.MaintenancePlan.Status.ENDED, "
                + "p.updatedAt = CURRENT_TIMESTAMP, p.version = p.version + 1 "
                + "where p.status <> com.crmplatform.operations.maintenance.domain.MaintenancePlan.Status.ENDED "
                + "and p.endDate < :today and p.deletedAt is null")
        int endExpired(LocalDate today);
    }

    public interface MaintenanceOccurrenceRepository extends JpaRepository<MaintenanceOccurrence, UUID> {

        boolean existsByPlanIdAndDueDate(UUID planId, LocalDate dueDate);

        List<MaintenanceOccurrence> findByPlanIdOrderByDueDateDesc(UUID planId);

        /** Open preventive jobs past their due date (tenant-filtered). */
        @Query("select o from MaintenanceOccurrence o, com.crmplatform.operations.jobs.domain.Job j "
                + "where j.id = o.jobId and o.dueDate < :today "
                + "and j.status not in (com.crmplatform.operations.jobs.domain.Job.Status.COMPLETED, "
                + "com.crmplatform.operations.jobs.domain.Job.Status.CANCELLED) order by o.dueDate")
        List<MaintenanceOccurrence> overdue(LocalDate today);

        @Query(value = "select o.id, o.workspace_id from maintenance_occurrences o join jobs j on j.id = o.job_id "
                + "where o.overdue_notified_at is null and o.due_date < :today and j.deleted_at is null "
                + "and j.status not in ('COMPLETED', 'CANCELLED')", nativeQuery = true)
        List<Object[]> overdueNotNotified(LocalDate today);
    }
}

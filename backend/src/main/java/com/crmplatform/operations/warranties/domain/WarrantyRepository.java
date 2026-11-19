package com.crmplatform.operations.warranties.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WarrantyRepository extends JpaRepository<Warranty, UUID>, JpaSpecificationExecutor<Warranty> {

    List<Warranty> findByProjectId(UUID projectId);

    List<Warranty> findByProjectIdInOrderByEndDateAsc(java.util.Collection<UUID> projectIds);

    @Query("select w from Warranty w where w.projectId in :projectIds or w.jobId in :jobIds order by w.endDate")
    List<Warranty> findByProjectsOrJobs(java.util.Collection<UUID> projectIds, java.util.Collection<UUID> jobIds);

    List<Warranty> findByJobId(UUID jobId);

    List<Warranty> findByEquipmentIdOrderByEndDateAsc(UUID equipmentId);

    /** Platform scope (native, no tenant filter): [id, workspace_id] of warranties due for their reminder. */
    @Query(value = "select w.id, w.workspace_id from warranties w where w.deleted_at is null and w.status = 'ACTIVE' "
            + "and w.reminder_days > 0 and w.reminder_sent_at is null and w.end_date >= :today "
            + "and w.end_date - w.reminder_days <= :today", nativeQuery = true)
    List<Object[]> dueForReminder(LocalDate today);

    @Query("select w from Warranty w join w.tagIds t where t = :tagId")
    List<Warranty> findByTag(UUID tagId);

    /** BR-065: flips only ACTIVE warranties whose end date is before {@code today}; idempotent. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Warranty w set w.status = com.crmplatform.operations.warranties.domain.Warranty.Status.EXPIRED, "
            + "w.updatedAt = :now, w.version = w.version + 1 "
            + "where w.status = com.crmplatform.operations.warranties.domain.Warranty.Status.ACTIVE "
            + "and w.endDate < :today and w.deletedAt is null")
    int expireEndedBefore(LocalDate today, Instant now);
}

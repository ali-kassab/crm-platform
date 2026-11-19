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

    List<Warranty> findByJobId(UUID jobId);

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

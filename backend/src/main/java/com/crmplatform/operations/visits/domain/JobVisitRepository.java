package com.crmplatform.operations.visits.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface JobVisitRepository extends JpaRepository<JobVisit, UUID>, JpaSpecificationExecutor<JobVisit> {

    List<JobVisit> findByJobIdOrderByScheduledStartAsc(UUID jobId);

    /**
     * Marks SCHEDULED visits whose window ended before {@code cutoff} as MISSED. Only SCHEDULED rows
     * change, so running it again (or on several instances) is harmless.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update JobVisit v set v.status = com.crmplatform.operations.visits.domain.JobVisit.Status.MISSED, "
            + "v.updatedAt = :now, v.version = v.version + 1 "
            + "where v.status = com.crmplatform.operations.visits.domain.JobVisit.Status.SCHEDULED "
            + "and v.scheduledEnd < :cutoff and v.deletedAt is null")
    int markMissedBefore(Instant cutoff, Instant now);
}

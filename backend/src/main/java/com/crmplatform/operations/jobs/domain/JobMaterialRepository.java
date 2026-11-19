package com.crmplatform.operations.jobs.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobMaterialRepository extends JpaRepository<JobMaterial, UUID> {

    List<JobMaterial> findByJobIdOrderByCreatedAtAsc(UUID jobId);

    @Query("select coalesce(sum(m.quantity * m.unitCost), 0) from JobMaterial m where m.jobId = :jobId")
    BigDecimal costOfJob(UUID jobId);

    /** Materials of the project's jobs (deleted jobs excluded through the job's own restriction). */
    @Query("select coalesce(sum(m.quantity * m.unitCost), 0) from JobMaterial m, Job j "
            + "where m.jobId = j.id and j.projectId = :projectId")
    BigDecimal costOfProject(UUID projectId);

    @Query("select coalesce(sum(m.quantity * m.unitCost), 0) from JobMaterial m, Job j "
            + "where m.jobId = j.id and j.contactId = :contactId")
    BigDecimal costOfContact(UUID contactId);
}

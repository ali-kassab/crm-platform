package com.crmplatform.operations.jobs.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    List<Job> findByProjectId(UUID projectId);

    List<Job> findByProjectIdInOrderByStartDateDesc(Collection<UUID> projectIds);

    /** Rows of {projectId, Σ budget} over the projects' active jobs (BR-055). */
    @Query("select j.projectId, sum(j.budget) from Job j where j.projectId in :projectIds group by j.projectId")
    List<Object[]> budgetTotals(Collection<UUID> projectIds);

    @Query("select j from Job j join j.equipmentIds e where e = :equipmentId order by j.createdAt desc")
    java.util.List<Job> findByEquipment(UUID equipmentId);

    @Query("select j from Job j join j.tagIds t where t = :tagId")
    List<Job> findByTag(UUID tagId);

    List<Job> findByContactIdOrderByCreatedAtDesc(UUID contactId);

    @Query("select coalesce(sum(j.budget), 0) from Job j where j.contactId = :contactId and j.projectId is null")
    java.math.BigDecimal standaloneBudgetTotalOfContact(UUID contactId);

    boolean existsByContactId(UUID contactId);

    boolean existsByLocationId(UUID locationId);
}

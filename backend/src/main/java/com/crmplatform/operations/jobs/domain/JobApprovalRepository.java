package com.crmplatform.operations.jobs.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApprovalRepository extends JpaRepository<JobApproval, UUID> {

    List<JobApproval> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}

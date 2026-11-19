package com.crmplatform.operations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the operations module for higher modules (finance, reporting, automation). */
public interface Jobs {

    Optional<JobSummary> find(UUID jobId);

    /** Active jobs of the given projects, newest start first. */
    List<JobOverview> ofProjects(Collection<UUID> projectIds);

    /** All active jobs (project and standalone) of a customer, newest first. */
    List<JobOverview> ofContact(UUID contactId);

    /** Σ budgets of the customer's standalone (project-less) jobs; part of the customer's contract. */
    BigDecimal standaloneBudgetTotalOfContact(UUID contactId);

    /** Σ quantity × unit cost of materials used (D12) — on a job, a project's jobs, or a customer's jobs. */
    BigDecimal materialsCostOfJob(UUID jobId);

    BigDecimal materialsCostOfProject(UUID projectId);

    BigDecimal materialsCostOfContact(UUID contactId);

    /** {@code projectId} is null for a standalone service job. */
    record JobSummary(UUID id, UUID projectId, String title, BigDecimal budget, UUID contactId, UUID locationId) {
    }

    record JobOverview(UUID id, UUID projectId, String title, LocalDate startDate, LocalDate estimatedEndDate,
            BigDecimal budget, String type, String status, String priority, UUID locationId) {
    }
}

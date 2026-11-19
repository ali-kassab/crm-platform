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

    record JobSummary(UUID id, UUID projectId, String title, BigDecimal budget) {
    }

    record JobOverview(UUID id, UUID projectId, String title, LocalDate startDate, LocalDate estimatedEndDate,
            BigDecimal budget) {
    }
}

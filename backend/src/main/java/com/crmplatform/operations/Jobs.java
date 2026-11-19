package com.crmplatform.operations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Public API of the operations module for higher modules (finance, automation). */
public interface Jobs {

    Optional<JobSummary> find(UUID jobId);

    record JobSummary(UUID id, UUID projectId, String title, BigDecimal budget) {
    }
}

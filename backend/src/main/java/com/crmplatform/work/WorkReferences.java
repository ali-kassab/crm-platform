package com.crmplatform.work;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Hooks implemented by the operations module (which depends on work), to avoid a cycle. */
public final class WorkReferences {

    private WorkReferences() {
    }

    /** Jobs as seen from the work module (task parents, project budget consumption). */
    public interface JobDirectory {

        Optional<JobRef> job(UUID jobId);

        /** Σ budgets of the project's active jobs, keyed by project id (BR-055). */
        Map<UUID, BigDecimal> jobBudgetTotals(Collection<UUID> projectIds);

        record JobRef(UUID id, UUID projectId, String title) {
        }
    }
}

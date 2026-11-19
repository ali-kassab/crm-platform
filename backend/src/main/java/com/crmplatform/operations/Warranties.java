package com.crmplatform.operations;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Public read API of warranties for higher modules (reporting). */
public interface Warranties {

    /** Active (not deleted) warranties linked to the given projects, ending soonest first. */
    List<WarrantyOverview> ofProjects(Collection<UUID> projectIds);

    /** Warranties linked to any of the projects or any of the jobs (standalone jobs have no project). */
    List<WarrantyOverview> ofProjectsOrJobs(Collection<UUID> projectIds, Collection<UUID> jobIds);

    record WarrantyOverview(UUID id, UUID projectId, UUID jobId, String title, String provider, String status,
            LocalDate startDate, LocalDate endDate) {
    }
}

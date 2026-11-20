package com.crmplatform.operations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public read API for printing a technical visit report (R12, reporting module). Every method applies
 * the same access rule as {@code GET /visits/{id}}: technicians only see visits assigned to them or to
 * their jobs; anything else is 404.
 */
public interface VisitReports {

    /** Everything a printed report shows. Material costs are left out on purpose (customer-facing). */
    VisitReportDocument document(UUID visitId);

    /**
     * Stored content of one of the visit's photos or of the report's customer signature; empty if the
     * file does not belong to this visit.
     */
    Optional<Image> image(UUID visitId, UUID fileId);

    record VisitReportDocument(UUID visitId, String visitStatus, String outcome, Instant scheduledStart,
            Instant scheduledEnd, Instant actualStart, Instant actualEnd, String visitNotes, Set<UUID> assigneeUserIds,
            UUID jobId, String jobTitle, String jobType, String jobPriority, String reportedIssue, UUID contactId,
            UUID locationId, Report report, List<Material> materials, List<Photo> photos) {
    }

    /** {@code null} fields were left empty by the technician. */
    record Report(String reportedProblem, String findings, String diagnosis, String workPerformed,
            String recommendations, String notes, String customerName, Instant customerConfirmedAt,
            UUID signatureFileId) {
    }

    record Material(String name, BigDecimal quantity, String unit) {
    }

    /** {@code purpose} is BEFORE, DURING, AFTER, … or null; only images are listed. */
    record Photo(UUID fileId, String purpose) {
    }

    record Image(String contentType, byte[] bytes) {
    }
}

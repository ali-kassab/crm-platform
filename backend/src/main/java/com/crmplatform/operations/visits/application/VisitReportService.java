package com.crmplatform.operations.visits.application;

import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.files.Files.FileSummary;
import com.crmplatform.operations.visits.domain.JobVisit;
import com.crmplatform.operations.visits.domain.VisitReport;
import com.crmplatform.operations.visits.domain.VisitReportRepository;
import com.crmplatform.shared.events.AuditTrail;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Technical visit reports and visit photos (directive §5E/§5F). Same access rule as the visit:
 * technicians only for visits assigned to them or their jobs.
 */
@Service
public class VisitReportService {

    static final String VISIT_OWNER = "VISIT";
    static final String REPORT_OWNER = "VISIT_REPORT";

    private final VisitService visits;
    private final VisitReportRepository reports;
    private final Files files;
    private final AuditTrail audit;
    private final Clock clock;

    public VisitReportService(VisitService visits, VisitReportRepository reports, Files files, AuditTrail audit,
            Clock clock) {
        this.visits = visits;
        this.reports = reports;
        this.files = files;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<VisitReport> report(UUID visitId) {
        visits.get(visitId); // access check
        return reports.findByVisitId(visitId);
    }

    /** Creates or replaces the report (PUT semantics). */
    @Transactional
    public VisitReport save(UUID visitId, ReportData d) {
        JobVisit visit = visits.get(visitId);
        VisitReport report = reports.findByVisitId(visitId).orElseGet(() -> new VisitReport(visitId));
        report.write(d.reportedProblem(), d.findings(), d.diagnosis(), d.workPerformed(), d.recommendations(),
                d.notes());
        if (d.customerSignatureFileId() != null) {
            files.attach(List.of(d.customerSignatureFileId()), FileKind.IMAGE, REPORT_OWNER, visitId,
                    "customerSignatureFileId");
        }
        report.confirmBy(blankToNull(d.customerName()), d.customerSignatureFileId(), Instant.now(clock));
        VisitReport saved = reports.save(report);
        audit.action("REPORT", "VISIT", visitId, null, visit.getJobId(),
                Map.of("confirmedBy", saved.getCustomerName() == null ? "" : saved.getCustomerName()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FileSummary> photos(UUID visitId) {
        visits.get(visitId);
        return files.ownedBy(VISIT_OWNER, visitId);
    }

    /** Attaches uploaded photos/documents (their purpose was set at upload: BEFORE, AFTER, …). */
    @Transactional
    public List<FileSummary> addFiles(UUID visitId, Collection<UUID> fileIds) {
        visits.get(visitId);
        files.attach(fileIds, null, VISIT_OWNER, visitId, "fileIds");
        return files.ownedBy(VISIT_OWNER, visitId);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public record ReportData(String reportedProblem, String findings, String diagnosis, String workPerformed,
            String recommendations, String notes, String customerName, UUID customerSignatureFileId) {
    }
}

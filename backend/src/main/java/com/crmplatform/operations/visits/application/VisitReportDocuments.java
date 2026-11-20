package com.crmplatform.operations.visits.application;

import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.operations.VisitReports;
import com.crmplatform.operations.jobs.application.JobService;
import com.crmplatform.operations.jobs.domain.Job;
import com.crmplatform.operations.jobs.domain.JobMaterialRepository;
import com.crmplatform.operations.visits.domain.JobVisit;
import com.crmplatform.operations.visits.domain.VisitReport;
import com.crmplatform.operations.visits.domain.VisitReportRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Data for the printed visit report (R12). Access is checked through {@link VisitService#get}. */
@Service
public class VisitReportDocuments implements VisitReports {

    private final VisitService visits;
    private final JobService jobs;
    private final VisitReportRepository reports;
    private final JobMaterialRepository materials;
    private final Files files;

    public VisitReportDocuments(VisitService visits, JobService jobs, VisitReportRepository reports,
            JobMaterialRepository materials, Files files) {
        this.visits = visits;
        this.jobs = jobs;
        this.reports = reports;
        this.materials = materials;
        this.files = files;
    }

    @Override
    @Transactional(readOnly = true)
    public VisitReportDocument document(UUID visitId) {
        JobVisit v = visits.get(visitId); // 404 unless the caller may see this visit
        Job j = jobs.get(v.getJobId());
        Report report = reports.findByVisitId(visitId).map(VisitReportDocuments::report).orElse(null);
        List<Material> used = materials.findByVisitIdOrderByCreatedAtAsc(visitId).stream()
                .map(m -> new Material(m.getName(), m.getQuantity(), m.getUnit())).toList();
        List<Photo> photos = files.ownedBy(VisitReportService.VISIT_OWNER, visitId).stream()
                .filter(f -> f.kind() == FileKind.IMAGE)
                .map(f -> new Photo(f.id(), f.purpose() == null ? null : f.purpose().name())).toList();
        return new VisitReportDocument(v.getId(), v.getStatus().name(), name(v.getOutcome()), v.getScheduledStart(),
                v.getScheduledEnd(), v.getActualStartAt(), v.getActualEndAt(), v.getNotes(), v.getAssigneeUserIds(),
                j.getId(), j.getTitle(), name(j.getType()), name(j.getPriority()), j.getReportedIssue(),
                j.getContactId(), j.getLocationId(), report, used, photos);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Image> image(UUID visitId, UUID fileId) {
        visits.get(visitId); // access check
        UUID signature = reports.findByVisitId(visitId).map(VisitReport::getCustomerSignatureId).orElse(null);
        String owner = Objects.equals(fileId, signature) ? VisitReportService.REPORT_OWNER
                : VisitReportService.VISIT_OWNER;
        return files.contentOfOwned(fileId, owner, visitId)
                .filter(c -> c.kind() == FileKind.IMAGE)
                .map(c -> new Image(c.contentType(), c.bytes()));
    }

    private static Report report(VisitReport r) {
        return new Report(r.getReportedProblem(), r.getFindings(), r.getDiagnosis(), r.getWorkPerformed(),
                r.getRecommendations(), r.getNotes(), r.getCustomerName(), r.getCustomerConfirmedAt(),
                r.getCustomerSignatureId());
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }
}

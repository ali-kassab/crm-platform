package com.crmplatform.operations.visits.web;

import com.crmplatform.files.Files.FileSummary;
import com.crmplatform.operations.visits.application.VisitReportService;
import com.crmplatform.operations.visits.application.VisitReportService.ReportData;
import com.crmplatform.operations.visits.domain.VisitReport;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Technical visit report and visit photos/documents (directive §5E/§5F). */
@RestController
@RequestMapping("/api/v1/visits/{visitId}")
@Tag(name = "Visits")
public class VisitReportController {

    private final VisitReportService reports;

    public VisitReportController(VisitReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    public ResponseEntity<ReportResponse> report(@PathVariable UUID visitId) {
        return reports.report(visitId).map(r -> ResponseEntity.ok(ReportResponse.of(r)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Creates or replaces the report; a {@code customerName} records the customer's confirmation. */
    @PutMapping("/report")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public ReportResponse save(@PathVariable UUID visitId, @Valid @RequestBody ReportRequest body) {
        return ReportResponse.of(reports.save(visitId, new ReportData(body.reportedProblem(), body.findings(),
                body.diagnosis(), body.workPerformed(), body.recommendations(), body.notes(), body.customerName(),
                body.customerSignatureFileId())));
    }

    @GetMapping("/files")
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    public List<FileRef> files(@PathVariable UUID visitId) {
        return reports.photos(visitId).stream().map(FileRef::of).toList();
    }

    @PostMapping("/files")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public List<FileRef> addFiles(@PathVariable UUID visitId, @Valid @RequestBody AddFilesRequest body) {
        return reports.addFiles(visitId, body.fileIds()).stream().map(FileRef::of).toList();
    }

    public record ReportRequest(@Size(max = 2000) String reportedProblem, @Size(max = 4000) String findings,
            @Size(max = 4000) String diagnosis, @Size(max = 4000) String workPerformed,
            @Size(max = 4000) String recommendations, @Size(max = 4000) String notes,
            @Size(max = 150) String customerName, UUID customerSignatureFileId) {
    }

    public record ReportResponse(UUID id, UUID visitId, String reportedProblem, String findings, String diagnosis,
            String workPerformed, String recommendations, String notes, String customerName,
            Instant customerConfirmedAt, UUID customerSignatureFileId, Instant updatedAt, long version) {
        static ReportResponse of(VisitReport r) {
            return new ReportResponse(r.getId(), r.getVisitId(), r.getReportedProblem(), r.getFindings(),
                    r.getDiagnosis(), r.getWorkPerformed(), r.getRecommendations(), r.getNotes(), r.getCustomerName(),
                    r.getCustomerConfirmedAt(), r.getCustomerSignatureId(), r.getUpdatedAt(), r.getVersion());
        }
    }

    public record AddFilesRequest(@NotEmpty @Size(max = 50) List<UUID> fileIds) {
    }

    public record FileRef(UUID id, String kind, String name, String contentType, long sizeBytes, String purpose,
            boolean hasThumbnail) {
        static FileRef of(FileSummary f) {
            return new FileRef(f.id(), f.kind().name(), f.name(), f.contentType(), f.sizeBytes(),
                    f.purpose() == null ? null : f.purpose().name(), f.hasThumbnail());
        }
    }
}

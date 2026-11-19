package com.crmplatform.operations.jobs.web;

import com.crmplatform.operations.jobs.application.JobExecutionService;
import com.crmplatform.operations.jobs.application.JobExecutionService.Decision;
import com.crmplatform.operations.jobs.application.JobExecutionService.MaterialData;
import com.crmplatform.operations.jobs.domain.JobApproval;
import com.crmplatform.operations.jobs.domain.JobMaterial;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Materials used on a job (§5I, D12) and customer approvals (§5J, D14). */
@RestController
@RequestMapping("/api/v1/jobs/{jobId}")
@Tag(name = "Jobs")
public class JobExecutionController {

    private final JobExecutionService execution;

    public JobExecutionController(JobExecutionService execution) {
        this.execution = execution;
    }

    @GetMapping("/materials")
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    public MaterialsResponse materials(@PathVariable UUID jobId) {
        List<MaterialResponse> lines = execution.materials(jobId).stream().map(MaterialResponse::of).toList();
        return new MaterialsResponse(lines, money(execution.materialsCost(jobId)));
    }

    @PostMapping("/materials")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public ResponseEntity<MaterialResponse> addMaterial(@PathVariable UUID jobId, @Valid @RequestBody MaterialRequest body) {
        JobMaterial m = execution.addMaterial(jobId, new MaterialData(body.visitId(), body.name(), body.quantity(),
                body.unit(), body.unitCost(), body.note()));
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + jobId + "/materials/" + m.getId()))
                .body(MaterialResponse.of(m));
    }

    @DeleteMapping("/materials/{materialId}")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMaterial(@PathVariable UUID jobId, @PathVariable UUID materialId) {
        execution.deleteMaterial(jobId, materialId);
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    public List<ApprovalResponse> approvals(@PathVariable UUID jobId) {
        return execution.approvals(jobId).stream().map(ApprovalResponse::of).toList();
    }

    /** Records an approval request; include {@code decision} to record an on-site sign-off at once. */
    @PostMapping("/approvals")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public ResponseEntity<ApprovalResponse> requestApproval(@PathVariable UUID jobId,
            @Valid @RequestBody ApprovalRequest body) {
        JobApproval a = execution.requestApproval(jobId, body.kind(), body.notes(),
                body.decision() == null ? null : body.decision().toDecision());
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + jobId + "/approvals/" + a.getId()))
                .body(ApprovalResponse.of(a));
    }

    @PostMapping("/approvals/{approvalId}/decision")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public ApprovalResponse decide(@PathVariable UUID jobId, @PathVariable UUID approvalId,
            @Valid @RequestBody DecisionRequest body) {
        return ApprovalResponse.of(execution.decide(jobId, approvalId, body.toDecision()));
    }

    private static String money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ------------------------------------------------------------------ DTOs

    public record MaterialRequest(UUID visitId, @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal quantity,
            @Size(max = 20) String unit,
            @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal unitCost,
            @Size(max = 500) String note) {
    }

    public record MaterialResponse(UUID id, UUID visitId, String name, String quantity, String unit, String unitCost,
            String lineCost, String note, UUID createdBy, Instant createdAt) {
        static MaterialResponse of(JobMaterial m) {
            return new MaterialResponse(m.getId(), m.getVisitId(), m.getName(), m.getQuantity().stripTrailingZeros()
                    .toPlainString(), m.getUnit(), money(m.getUnitCost()), money(m.lineCost()), m.getNote(),
                    m.getCreatedBy(), m.getCreatedAt());
        }
    }

    public record MaterialsResponse(List<MaterialResponse> lines, String totalCost) {
    }

    public record DecisionRequest(@NotBlank @Pattern(regexp = "^(APPROVED|REJECTED)$") String status,
            @NotBlank @Size(max = 150) String approverName, @Size(max = 2000) String notes, UUID signatureFileId) {
        Decision toDecision() {
            return new Decision(status, approverName, notes, signatureFileId);
        }
    }

    public record ApprovalRequest(@NotBlank @Pattern(regexp = "^(QUOTATION|WORK|COMPLETION)$") String kind,
            @Size(max = 2000) String notes, @Valid DecisionRequest decision) {
    }

    public record ApprovalResponse(UUID id, UUID jobId, String kind, String status, String approverName,
            Instant decidedAt, String notes, UUID signatureFileId, UUID createdBy, Instant createdAt) {
        static ApprovalResponse of(JobApproval a) {
            return new ApprovalResponse(a.getId(), a.getJobId(), a.getKind().name(), a.getStatus().name(),
                    a.getApproverName(), a.getDecidedAt(), a.getNotes(), a.getSignatureFileId(), a.getCreatedBy(),
                    a.getCreatedAt());
        }
    }
}

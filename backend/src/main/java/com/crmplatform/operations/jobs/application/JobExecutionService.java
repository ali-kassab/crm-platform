package com.crmplatform.operations.jobs.application;

import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.operations.jobs.domain.Job;
import com.crmplatform.operations.jobs.domain.JobApproval;
import com.crmplatform.operations.jobs.domain.JobApprovalRepository;
import com.crmplatform.operations.jobs.domain.JobMaterial;
import com.crmplatform.operations.jobs.domain.JobMaterialRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.security.Permissions;
import com.crmplatform.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materials used on a job (D12, cost lines) and customer approvals (D14, staff-recorded). Access
 * follows the job: technicians only on their assigned jobs.
 */
@Service
public class JobExecutionService {

    static final String APPROVAL_OWNER = "JOB_APPROVAL";

    private final JobService jobs;
    private final JobMaterialRepository materials;
    private final JobApprovalRepository approvals;
    private final Files files;
    private final AuditTrail audit;
    private final Clock clock;
    private final com.crmplatform.operations.visits.domain.JobVisitRepository visits;

    public JobExecutionService(JobService jobs, JobMaterialRepository materials, JobApprovalRepository approvals,
            Files files, AuditTrail audit, Clock clock, com.crmplatform.operations.visits.domain.JobVisitRepository visits) {
        this.visits = visits;
        this.jobs = jobs;
        this.materials = materials;
        this.approvals = approvals;
        this.files = files;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ materials

    @Transactional(readOnly = true)
    public List<JobMaterial> materials(UUID jobId) {
        jobs.get(jobId);
        return materials.findByJobIdOrderByCreatedAtAsc(jobId);
    }

    @Transactional
    public JobMaterial addMaterial(UUID jobId, MaterialData d) {
        Job job = jobs.get(jobId);
        if (d.quantity() == null || d.quantity().signum() <= 0) {
            throw ApiException.badRequest("VALIDATION_ERROR", "quantity must be greater than 0");
        }
        if (d.visitId() != null && visits.findById(d.visitId()).filter(v -> v.getJobId().equals(jobId)).isEmpty()) {
            throw ApiException.referenceNotFound("visitId");
        }
        JobMaterial saved = materials.save(new JobMaterial(jobId, d.visitId(), d.name(), d.quantity(), d.unit(),
                d.unitCost(), d.note()));
        audit.created("MATERIAL", saved.getId(), job.getProjectId(), jobId,
                Map.of("name", saved.getName(), "quantity", saved.getQuantity().toPlainString(),
                        "lineCost", saved.lineCost().toPlainString()));
        return saved;
    }

    /** The person who recorded it, or a dispatcher ({@code ops:write}), may remove a line. */
    @Transactional
    public void deleteMaterial(UUID jobId, UUID materialId) {
        Job job = jobs.get(jobId);
        JobMaterial m = materials.findById(materialId).filter(x -> x.getJobId().equals(jobId))
                .orElseThrow(() -> ApiException.notFound("Material"));
        var user = CurrentUser.require();
        if (!user.has(Permissions.OPS_WRITE) && !user.userId().equals(m.getCreatedBy())) {
            throw ApiException.forbidden("Only the person who recorded it or a dispatcher can remove this line");
        }
        m.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        audit.deleted("MATERIAL", materialId, job.getProjectId(), jobId);
    }

    @Transactional(readOnly = true)
    public BigDecimal materialsCost(UUID jobId) {
        return materials.costOfJob(jobId);
    }

    // ------------------------------------------------------------------ approvals

    @Transactional(readOnly = true)
    public List<JobApproval> approvals(UUID jobId) {
        jobs.get(jobId);
        return approvals.findByJobIdOrderByCreatedAtAsc(jobId);
    }

    /** Requests an approval; passing a decision records it at once (e.g. signed on site). */
    @Transactional
    public JobApproval requestApproval(UUID jobId, String kind, String notes, Decision decision) {
        Job job = jobs.get(jobId);
        JobApproval approval = approvals.save(new JobApproval(jobId, parseKind(kind), notes));
        audit.created("APPROVAL", approval.getId(), job.getProjectId(), jobId, Map.of("kind", approval.getKind().name()));
        if (decision != null) {
            applyDecision(job, approval, decision);
        }
        return approval;
    }

    @Transactional
    public JobApproval decide(UUID jobId, UUID approvalId, Decision decision) {
        Job job = jobs.get(jobId);
        JobApproval approval = approvals.findById(approvalId).filter(a -> a.getJobId().equals(jobId))
                .orElseThrow(() -> ApiException.notFound("Approval"));
        applyDecision(job, approval, decision);
        return approvals.saveAndFlush(approval);
    }

    private void applyDecision(Job job, JobApproval approval, Decision d) {
        JobApproval.Status status;
        try {
            status = JobApproval.Status.valueOf(d.status() == null ? "" : d.status());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "decision must be APPROVED or REJECTED");
        }
        if (d.signatureFileId() != null) {
            files.attach(List.of(d.signatureFileId()), FileKind.IMAGE, APPROVAL_OWNER, approval.getId(),
                    "signatureFileId");
        }
        approval.decide(status, d.approverName(), d.notes(), d.signatureFileId(), Instant.now(clock));
        audit.action("DECIDE", "APPROVAL", approval.getId(), job.getProjectId(), job.getId(),
                Map.of("kind", approval.getKind().name(), "status", status.name(), "approver", d.approverName()));
    }

    private static JobApproval.Kind parseKind(String value) {
        try {
            return JobApproval.Kind.valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "kind must be QUOTATION, WORK or COMPLETION");
        }
    }

    public record MaterialData(UUID visitId, String name, BigDecimal quantity, String unit, BigDecimal unitCost,
            String note) {
    }

    public record Decision(String status, String approverName, String notes, UUID signatureFileId) {
    }
}

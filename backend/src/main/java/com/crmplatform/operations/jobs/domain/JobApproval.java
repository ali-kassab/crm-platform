package com.crmplatform.operations.jobs.domain;

import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * A customer approval recorded by staff (directive §5J, D14): quotation, work or completion
 * approval, with the approver's name, time, notes and an optional signature image. No customer
 * portal in this phase.
 */
@Entity
@Table(name = "job_approvals")
@SQLRestriction("deleted_at is null")
public class JobApproval extends TenantEntity {

    public enum Kind { QUOTATION, WORK, COMPLETION }

    public enum Status { PENDING, APPROVED, REJECTED }

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12, updatable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(name = "approver_name", length = 150)
    private String approverName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "signature_file_id")
    private UUID signatureFileId;

    protected JobApproval() {
    }

    public JobApproval(UUID jobId, Kind kind, String notes) {
        this.jobId = jobId;
        this.kind = kind;
        this.notes = notes;
    }

    /** One decision per approval; a changed mind is a new approval, so the history stays honest. */
    public void decide(Status decision, String approver, String decisionNotes, UUID signature, Instant at) {
        if (status != Status.PENDING) {
            throw ApiException.conflict("ALREADY_DECIDED", "This approval has already been decided");
        }
        if (decision == Status.PENDING) {
            throw ApiException.badRequest("VALIDATION_ERROR", "decision must be APPROVED or REJECTED");
        }
        if (approver == null || approver.isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "approverName is required");
        }
        this.status = decision;
        this.approverName = approver.trim();
        this.signatureFileId = signature;
        this.decidedAt = at;
        if (decisionNotes != null && !decisionNotes.isBlank()) {
            this.notes = decisionNotes;
        }
    }

    public UUID getJobId() {
        return jobId;
    }

    public Kind getKind() {
        return kind;
    }

    public Status getStatus() {
        return status;
    }

    public String getApproverName() {
        return approverName;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getSignatureFileId() {
        return signatureFileId;
    }
}

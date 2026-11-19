package com.crmplatform.operations.visits.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * The technician's report for one visit (directive §5E): what was reported, found, diagnosed and
 * done, recommendations, and the customer's confirmation (name, time, optional signature image).
 * Text is stored exactly as entered (Arabic or English).
 */
@Entity
@Table(name = "visit_reports")
@SQLRestriction("deleted_at is null")
public class VisitReport extends TenantEntity {

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(name = "reported_problem", length = 2000)
    private String reportedProblem;

    @Column(length = 4000)
    private String findings;

    @Column(length = 4000)
    private String diagnosis;

    @Column(name = "work_performed", length = 4000)
    private String workPerformed;

    @Column(length = 4000)
    private String recommendations;

    @Column(length = 4000)
    private String notes;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "customer_confirmed_at")
    private Instant customerConfirmedAt;

    @Column(name = "customer_signature_id")
    private UUID customerSignatureId;

    protected VisitReport() {
    }

    public VisitReport(UUID visitId) {
        this.visitId = visitId;
    }

    public void write(String reportedProblem, String findings, String diagnosis, String workPerformed,
            String recommendations, String notes) {
        this.reportedProblem = reportedProblem;
        this.findings = findings;
        this.diagnosis = diagnosis;
        this.workPerformed = workPerformed;
        this.recommendations = recommendations;
        this.notes = notes;
    }

    /** Records (or clears, with a null name) the customer's on-site confirmation. */
    public void confirmBy(String name, UUID signatureFileId, Instant at) {
        this.customerName = name;
        this.customerSignatureId = name == null ? null : signatureFileId;
        this.customerConfirmedAt = name == null ? null : at;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public String getReportedProblem() {
        return reportedProblem;
    }

    public String getFindings() {
        return findings;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getWorkPerformed() {
        return workPerformed;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public String getNotes() {
        return notes;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Instant getCustomerConfirmedAt() {
        return customerConfirmedAt;
    }

    public UUID getCustomerSignatureId() {
        return customerSignatureId;
    }
}

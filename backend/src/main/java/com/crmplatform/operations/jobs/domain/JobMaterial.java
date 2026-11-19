package com.crmplatform.operations.jobs.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * A material or spare part used on a job, optionally on a specific visit (directive §5I). A cost
 * line (D12): it counts in the job's profitability but is not a financial transaction. No stock or
 * warehouse management in this phase.
 */
@Entity
@Table(name = "job_materials")
@SQLRestriction("deleted_at is null")
public class JobMaterial extends TenantEntity {

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "visit_id", updatable = false)
    private UUID visitId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(length = 20)
    private String unit;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(length = 500)
    private String note;

    protected JobMaterial() {
    }

    public JobMaterial(UUID jobId, UUID visitId, String name, BigDecimal quantity, String unit, BigDecimal unitCost,
            String note) {
        this.jobId = jobId;
        this.visitId = visitId;
        this.name = name.trim();
        this.quantity = quantity;
        this.unit = unit;
        this.unitCost = unitCost == null ? BigDecimal.ZERO : unitCost;
        this.note = note;
    }

    public BigDecimal lineCost() {
        return quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getNote() {
        return note;
    }
}

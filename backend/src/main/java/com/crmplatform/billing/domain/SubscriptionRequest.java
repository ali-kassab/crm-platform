package com.crmplatform.billing.domain;

import com.crmplatform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A tenant's "I paid, please activate" request with the transfer receipt; reviewed by the platform admin. */
@Entity
@Table(name = "subscription_requests")
public class SubscriptionRequest extends BaseEntity {

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 7, updatable = false)
    private BillingPeriod billingPeriod;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private UUID paymentMethodId;

    @Column(name = "payer_reference", nullable = false, length = 100, updatable = false)
    private String payerReference;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(name = "receipt_file_id", nullable = false, updatable = false)
    private UUID receiptFileId;

    @Column(length = 1000, updatable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    protected SubscriptionRequest() {
    }

    public SubscriptionRequest(UUID workspaceId, Plan plan, BillingPeriod period, UUID paymentMethodId,
            String payerReference, LocalDate paidOn, UUID receiptFileId, String note, UUID requestedBy) {
        this.workspaceId = workspaceId;
        this.planId = plan.getId();
        this.billingPeriod = period;
        this.amount = plan.priceFor(period); // the price at request time
        this.currency = plan.getCurrency();
        this.paymentMethodId = paymentMethodId;
        this.payerReference = payerReference.trim();
        this.paidOn = paidOn;
        this.receiptFileId = receiptFileId;
        this.note = note;
        this.requestedBy = requestedBy;
    }

    public void review(Status outcome, UUID adminId, String note, Instant at) {
        this.status = outcome;
        this.reviewedBy = adminId;
        this.reviewNote = note;
        this.reviewedAt = at;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public String getPayerReference() {
        return payerReference;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public UUID getReceiptFileId() {
        return receiptFileId;
    }

    public String getNote() {
        return note;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }
}

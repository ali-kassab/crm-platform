package com.crmplatform.finance.transactions.domain;

import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * A money movement on a job (BR-090..093). Immutable once created: the only change is a single
 * void, and a database trigger rejects any other update.
 */
@Entity
@Table(name = "transactions")
@SQLRestriction("deleted_at is null")
public class Transaction extends TenantEntity {

    public enum Type { RECEIVED, PAID }

    public enum Method {
        CASH(false, false), CHECK(true, true), BANK_TRANSFER(true, false), CREDIT_CARD(true, false), OTHER(false, false);

        private final boolean needsBank;
        private final boolean needsCheckNumber;

        Method(boolean needsBank, boolean needsCheckNumber) {
            this.needsBank = needsBank;
            this.needsCheckNumber = needsCheckNumber;
        }

        public boolean needsBank() {
            return needsBank;
        }

        public boolean needsCheckNumber() {
            return needsCheckNumber;
        }
    }

    @Column(name = "job_id", updatable = false)
    private UUID jobId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "contact_id", nullable = false, updatable = false)
    private UUID contactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private Type type;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false)
    private String payee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 13, updatable = false)
    private Method method;

    @Column(name = "bank_name", length = 150, updatable = false)
    private String bankName;

    @Column(name = "check_number", length = 50, updatable = false)
    private String checkNumber;

    @Column(name = "commitment_date", nullable = false, updatable = false)
    private LocalDate commitmentDate;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "doc_notes", length = 1000, updatable = false)
    private String docNotes;

    @Column(name = "doc_number", length = 100, updatable = false)
    private String docNumber;

    @Column(name = "doc_date", updatable = false)
    private LocalDate docDate;

    @Column(name = "attachment_file_id", updatable = false)
    private UUID attachmentFileId;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    protected Transaction() {
    }

    public Transaction(Parties parties, Type type, BigDecimal amount, Details details) {
        this.jobId = parties.jobId();
        this.projectId = parties.projectId();
        this.contactId = parties.contactId();
        this.type = type;
        this.amount = amount;
        this.title = details.title().trim();
        this.payee = details.payee().trim();
        this.method = details.method();
        this.bankName = blankToNull(details.bankName());
        this.checkNumber = blankToNull(details.checkNumber());
        this.commitmentDate = details.commitmentDate();
        this.dueDate = details.dueDate();
        this.docNotes = blankToNull(details.docNotes());
        this.docNumber = blankToNull(details.docNumber());
        this.docDate = details.docDate();
        this.attachmentFileId = details.attachmentFileId();
    }

    /** NF-03: voids once; voided transactions drop out of the rollups (BR-093, EC-25). */
    public void voidWith(String reason, Instant at, UUID by) {
        if (voidedAt != null) {
            throw ApiException.conflict("ALREADY_VOIDED", "This transaction is already voided");
        }
        this.voidReason = reason.trim();
        this.voidedAt = at;
        this.voidedBy = by;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getContactId() {
        return contactId;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getTitle() {
        return title;
    }

    public String getPayee() {
        return payee;
    }

    public Method getMethod() {
        return method;
    }

    public String getBankName() {
        return bankName;
    }

    public String getCheckNumber() {
        return checkNumber;
    }

    public LocalDate getCommitmentDate() {
        return commitmentDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getDocNotes() {
        return docNotes;
    }

    public String getDocNumber() {
        return docNumber;
    }

    public LocalDate getDocDate() {
        return docDate;
    }

    public UUID getAttachmentFileId() {
        return attachmentFileId;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public UUID getVoidedBy() {
        return voidedBy;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    public record Parties(UUID jobId, UUID projectId, UUID contactId) {
    }

    public record Details(String title, String payee, Method method, String bankName, String checkNumber,
            LocalDate commitmentDate, LocalDate dueDate, String docNotes, String docNumber, LocalDate docDate,
            UUID attachmentFileId) {
    }
}

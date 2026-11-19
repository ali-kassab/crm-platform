package com.crmplatform.finance.transactions.application;

import com.crmplatform.crm.Contacts;
import com.crmplatform.files.Files;
import com.crmplatform.finance.transactions.domain.Transaction;
import com.crmplatform.finance.transactions.domain.Transaction.Details;
import com.crmplatform.finance.transactions.domain.Transaction.Method;
import com.crmplatform.finance.transactions.domain.Transaction.Parties;
import com.crmplatform.finance.transactions.domain.Transaction.Type;
import com.crmplatform.finance.transactions.domain.TransactionRepository;
import com.crmplatform.operations.Jobs;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.work.Projects;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recording, listing and voiding transactions (FR-FIN-01..03, BR-090..093). */
@Service
public class TransactionService {

    static final String OWNER = "TRANSACTION";

    private final TransactionRepository transactions;
    private final Jobs jobs;
    private final Projects projects;
    private final Contacts contacts;
    private final Files files;
    private final AuditTrail audit;
    private final Clock clock;

    public TransactionService(TransactionRepository transactions, Jobs jobs, Projects projects, Contacts contacts,
            Files files, AuditTrail audit, Clock clock) {
        this.transactions = transactions;
        this.jobs = jobs;
        this.projects = projects;
        this.contacts = contacts;
        this.files = files;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> search(TransactionFilter filter, Pageable pageable) {
        return transactions.findAll(TransactionSpecs.matching(filter), pageable);
    }

    @Transactional(readOnly = true)
    public Transaction get(UUID id) {
        return transactions.findById(id).orElseThrow(() -> ApiException.notFound("Transaction"));
    }

    @Transactional
    public Transaction record(TransactionData d) {
        Jobs.JobSummary job = jobs.find(d.jobId()).orElseThrow(() -> ApiException.referenceNotFound("jobId"));
        if (d.projectId() != null && !Objects.equals(d.projectId(), job.projectId())) {
            throw ApiException.badRequest("JOB_NOT_IN_PROJECT", "The job does not belong to the project"); // BR-091
        }
        Projects.ProjectSummary project = projects.find(job.projectId())
                .orElseThrow(() -> ApiException.referenceNotFound("projectId"));
        UUID contactId = d.contactId() != null ? d.contactId() : project.contactId(); // EC-26
        if (contacts.find(contactId).isEmpty()) {
            throw ApiException.referenceNotFound("contactId");
        }
        Method method = Method.valueOf(d.method());
        if (method.needsBank() && isBlank(d.bankName())) {
            throw fieldRequired("bankName", "bankName is required for " + method.name());
        }
        if (method.needsCheckNumber() && isBlank(d.checkNumber())) {
            throw fieldRequired("checkNumber", "checkNumber is required for CHECK");
        }
        if (d.dueDate().isBefore(d.commitmentDate())) {
            throw ApiException.badRequest("VALIDATION_ERROR", "dueDate must be on or after commitmentDate");
        }
        if (d.amount().signum() <= 0 || d.amount().stripTrailingZeros().scale() > 2) { // BR-090
            throw ApiException.badRequest("VALIDATION_ERROR", "amount must be greater than 0 with at most 2 decimals");
        }

        Transaction t = transactions.save(new Transaction(new Parties(job.id(), job.projectId(), contactId),
                Type.valueOf(d.type()), d.amount().setScale(2), new Details(d.title(), d.payee(), method,
                        d.bankName(), d.checkNumber(), d.commitmentDate(), d.dueDate(), d.docNotes(), d.docNumber(),
                        d.docDate(), d.attachmentFileId())));
        files.attach(d.attachmentFileId() == null ? List.of() : List.of(d.attachmentFileId()), null, OWNER, t.getId(),
                "attachmentFileId");
        audit.created("TRANSACTION", t.getId(), t.getProjectId(), t.getJobId(), snapshot(t));
        return t;
    }

    @Transactional
    public Transaction voidTransaction(UUID id, String reason) {
        Transaction t = get(id);
        if (reason == null || reason.trim().length() < 5) {
            throw fieldRequired("reason", "reason must be at least 5 characters");
        }
        t.voidWith(reason, Instant.now(clock), TenantContext.userId().orElse(null));
        transactions.saveAndFlush(t);
        audit.action("VOID", "TRANSACTION", id, t.getProjectId(), t.getJobId(), Map.of("reason", t.getVoidReason()));
        return t;
    }

    private static ApiException fieldRequired(String field, String message) {
        return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                Map.of("errors", List.of(Map.of("field", field, "code", "REQUIRED", "message", message))));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static Map<String, Object> snapshot(Transaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", t.getType().name());
        m.put("amount", t.getAmount().toPlainString());
        m.put("method", t.getMethod().name());
        m.put("title", t.getTitle());
        m.put("payee", t.getPayee());
        m.put("commitmentDate", t.getCommitmentDate().toString());
        m.put("dueDate", t.getDueDate().toString());
        return m;
    }

    public record TransactionData(UUID jobId, UUID projectId, UUID contactId, String type, BigDecimal amount,
            String title, String payee, String method, String bankName, String checkNumber, LocalDate commitmentDate,
            LocalDate dueDate, String docNotes, String docNumber, LocalDate docDate, UUID attachmentFileId) {
    }

    public record TransactionFilter(String type, String method, UUID jobId, UUID projectId, UUID contactId,
            LocalDate from, LocalDate to, boolean includeVoided, String q) {
    }
}

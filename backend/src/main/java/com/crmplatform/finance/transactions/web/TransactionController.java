package com.crmplatform.finance.transactions.web;

import com.crmplatform.finance.transactions.application.TransactionService;
import com.crmplatform.finance.transactions.application.TransactionService.TransactionData;
import com.crmplatform.finance.transactions.application.TransactionService.TransactionFilter;
import com.crmplatform.finance.transactions.domain.Transaction;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Transactions (FR-FIN-01..03). There is no update or delete: void instead (BR-093). */
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions")
public class TransactionController {

    static final String TYPE = "^(RECEIVED|PAID)$";
    static final String METHOD = "^(CASH|CHECK|BANK_TRANSFER|CREDIT_CARD|OTHER)$";
    private static final Map<String, String> SORT = Map.of("commitmentDate", "commitmentDate", "dueDate", "dueDate",
            "amount", "amount", "createdAt", "createdAt", "title", "title");

    private final TransactionService transactions;

    public TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('finance:read')")
    public PageResponse<TransactionResponse> list(@RequestParam(required = false) String type,
            @RequestParam(required = false) String method, @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeVoided, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        if ((type != null && !type.matches(TYPE)) || (method != null && !method.matches(METHOD))) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown transaction type or method");
        }
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.DESC, "commitmentDate"));
        return PageResponse.of(transactions.search(new TransactionFilter(type, method, jobId, projectId, contactId,
                from, to, includeVoided, q), pageable), TransactionResponse::of);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('finance:read')")
    public TransactionResponse get(@PathVariable UUID id) {
        return TransactionResponse.of(transactions.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('finance:write')")
    public ResponseEntity<TransactionResponse> record(@Valid @RequestBody TransactionRequest body) {
        Transaction t = transactions.record(body.toData());
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + t.getId())).body(TransactionResponse.of(t));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('finance:write')")
    public TransactionResponse voidTransaction(@PathVariable UUID id, @Valid @RequestBody VoidRequest body) {
        return TransactionResponse.of(transactions.voidTransaction(id, body.reason()));
    }

    // ------------------------------------------------------------------ DTOs

    public record TransactionRequest(
            UUID jobId,
            UUID projectId,
            UUID contactId,
            @NotBlank @Pattern(regexp = TYPE) String type,
            @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 255) String payee,
            @NotBlank @Pattern(regexp = METHOD) String method,
            @Size(max = 150) String bankName,
            @Size(max = 50) String checkNumber,
            @NotNull LocalDate commitmentDate,
            @NotNull LocalDate dueDate,
            @Size(max = 1000) String docNotes,
            @Size(max = 100) String docNumber,
            LocalDate docDate,
            UUID attachmentFileId) {

        TransactionData toData() {
            return new TransactionData(jobId, projectId, contactId, type, amount, title, payee, method, bankName,
                    checkNumber, commitmentDate, dueDate, docNotes, docNumber, docDate, attachmentFileId);
        }
    }

    public record VoidRequest(@NotBlank @Size(min = 5, max = 500) String reason) {
    }

    public record TransactionResponse(UUID id, UUID jobId, UUID projectId, UUID contactId, String type, String amount,
            String title, String payee, String method, String bankName, String checkNumber, LocalDate commitmentDate,
            LocalDate dueDate, String docNotes, String docNumber, LocalDate docDate, UUID attachmentFileId,
            boolean voided, Instant voidedAt, UUID voidedBy, String voidReason, UUID createdBy, Instant createdAt) {

        static TransactionResponse of(Transaction t) {
            return new TransactionResponse(t.getId(), t.getJobId(), t.getProjectId(), t.getContactId(),
                    t.getType().name(), t.getAmount().setScale(2).toPlainString(), t.getTitle(), t.getPayee(),
                    t.getMethod().name(), t.getBankName(), t.getCheckNumber(), t.getCommitmentDate(), t.getDueDate(),
                    t.getDocNotes(), t.getDocNumber(), t.getDocDate(), t.getAttachmentFileId(), t.isVoided(),
                    t.getVoidedAt(), t.getVoidedBy(), t.getVoidReason(), t.getCreatedBy(), t.getCreatedAt());
        }
    }
}

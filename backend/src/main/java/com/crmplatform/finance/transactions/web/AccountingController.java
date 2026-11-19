package com.crmplatform.finance.transactions.web;

import com.crmplatform.finance.transactions.application.AccountingService;
import com.crmplatform.finance.transactions.application.AccountingService.Summary;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Accounting rollups for jobs, projects and contacts (FR-FIN-04, FR-CRM-09). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Accounting")
public class AccountingController {

    private final AccountingService accounting;
    private final com.crmplatform.finance.transactions.application.OutstandingBalances outstanding;

    public AccountingController(AccountingService accounting,
            com.crmplatform.finance.transactions.application.OutstandingBalances outstanding) {
        this.accounting = accounting;
        this.outstanding = outstanding;
    }

    /** Customers who still owe money (contract − received &gt; 0), largest balance first. */
    @GetMapping("/accounting/outstanding")
    @PreAuthorize("hasAuthority('finance:read')")
    public OutstandingResponse outstanding(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        var r = outstanding.list(page, size);
        return new OutstandingResponse(r.rows().stream().map(row -> new OutstandingRow(row.contactId(),
                row.customerName(), row.phone(), AccountingResponse.money(row.contract()),
                AccountingResponse.money(row.received()), AccountingResponse.money(row.remaining()),
                row.lastPaymentDate())).toList(), r.totalCustomers(), AccountingResponse.money(r.pageRemaining()));
    }

    public record OutstandingRow(UUID contactId, String customerName, String phone, String contract, String received,
            String remaining, java.time.LocalDate lastPaymentDate) {
    }

    public record OutstandingResponse(java.util.List<OutstandingRow> content, long totalElements, String pageRemaining) {
    }

    @GetMapping("/jobs/{id}/accounting")
    @PreAuthorize("hasAuthority('finance:read')")
    public AccountingResponse job(@PathVariable UUID id) {
        return AccountingResponse.of(accounting.job(id));
    }

    @GetMapping("/projects/{id}/accounting")
    @PreAuthorize("hasAuthority('finance:read')")
    public AccountingResponse project(@PathVariable UUID id) {
        return AccountingResponse.of(accounting.project(id));
    }

    @GetMapping("/contacts/{id}/accounting")
    @PreAuthorize("hasAuthority('finance:read')")
    public AccountingResponse contact(@PathVariable UUID id) {
        return AccountingResponse.of(accounting.contact(id));
    }

    public record AccountingResponse(String contract, String received, String actualCost, String materialsCost,
            String remaining,
            String profitLoss, long transactionCount, boolean overpaid) {

        static AccountingResponse of(Summary s) {
            return new AccountingResponse(money(s.contract()), money(s.received()), money(s.actualCost()),
                    money(s.materialsCost()),
                    money(s.remaining()), money(s.profitLoss()), s.transactionCount(), s.overpaid());
        }

        static String money(BigDecimal v) {
            return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}

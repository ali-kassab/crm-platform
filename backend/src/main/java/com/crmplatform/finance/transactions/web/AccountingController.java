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

    public AccountingController(AccountingService accounting) {
        this.accounting = accounting;
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

        private static String money(BigDecimal v) {
            return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}

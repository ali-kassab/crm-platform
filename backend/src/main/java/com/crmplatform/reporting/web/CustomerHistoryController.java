package com.crmplatform.reporting.web;

import com.crmplatform.finance.Accounting;
import com.crmplatform.operations.Jobs;
import com.crmplatform.operations.Warranties;
import com.crmplatform.reporting.application.CustomerHistoryService;
import com.crmplatform.reporting.application.CustomerHistoryService.History;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.security.Permissions;
import com.crmplatform.work.Projects;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Customer history (directive §5A): everything done for one customer, on one screen. */
@RestController
@Tag(name = "Contacts")
public class CustomerHistoryController {

    private final CustomerHistoryService history;

    public CustomerHistoryController(CustomerHistoryService history) {
        this.history = history;
    }

    @GetMapping("/api/v1/contacts/{id}/history")
    @PreAuthorize("hasAuthority('crm:read')")
    @Operation(summary = "Projects, jobs, warranties and (with finance:read) money of one customer")
    public HistoryResponse get(@PathVariable UUID id) {
        boolean finance = CurrentUser.require().has(Permissions.FINANCE_READ);
        return HistoryResponse.of(history.of(id, finance));
    }

    public record HistoryResponse(CustomerRef customer, List<ProjectItem> projects, List<JobItem> jobs,
            List<WarrantyItem> warranties, Totals accounting, List<TransactionItem> recentTransactions) {

        static HistoryResponse of(History h) {
            var c = h.contact();
            return new HistoryResponse(
                    new CustomerRef(c.id(), c.type(), c.fullName(), c.company(), c.email(), c.phone(), c.city()),
                    h.projects().stream().map(ProjectItem::of).toList(),
                    h.jobs().stream().map(JobItem::of).toList(),
                    h.warranties().stream().map(WarrantyItem::of).toList(),
                    h.accounting() == null ? null : Totals.of(h.accounting()),
                    h.recentTransactions() == null ? null
                            : h.recentTransactions().stream().map(TransactionItem::of).toList());
        }
    }

    public record CustomerRef(UUID id, String type, String name, String company, String email, String phone,
            String city) {
    }

    public record ProjectItem(UUID id, String name, String type, String budget, LocalDate startDate,
            LocalDate estimatedEndDate, UUID locationId) {
        static ProjectItem of(Projects.ProjectSummary p) {
            return new ProjectItem(p.id(), p.name(), p.type(), money(p.budget()), p.startDate(), p.estimatedEndDate(),
                    p.locationId());
        }
    }

    public record JobItem(UUID id, UUID projectId, String title, LocalDate startDate, LocalDate estimatedEndDate,
            String budget) {
        static JobItem of(Jobs.JobOverview j) {
            return new JobItem(j.id(), j.projectId(), j.title(), j.startDate(), j.estimatedEndDate(), money(j.budget()));
        }
    }

    public record WarrantyItem(UUID id, UUID projectId, UUID jobId, String title, String provider, String status,
            LocalDate startDate, LocalDate endDate) {
        static WarrantyItem of(Warranties.WarrantyOverview w) {
            return new WarrantyItem(w.id(), w.projectId(), w.jobId(), w.title(), w.provider(), w.status(),
                    w.startDate(), w.endDate());
        }
    }

    public record Totals(String contract, String received, String actualCost, String remaining, String profitLoss,
            long transactionCount, boolean overpaid) {
        static Totals of(Accounting.Totals t) {
            return new Totals(money(t.contract()), money(t.received()), money(t.actualCost()), money(t.remaining()),
                    money(t.profitLoss()), t.transactionCount(), t.overpaid());
        }
    }

    public record TransactionItem(UUID id, UUID jobId, UUID projectId, String type, String amount, String title,
            String method, LocalDate commitmentDate, LocalDate dueDate) {
        static TransactionItem of(Accounting.TransactionOverview t) {
            return new TransactionItem(t.id(), t.jobId(), t.projectId(), t.type(), money(t.amount()), t.title(),
                    t.method(), t.commitmentDate(), t.dueDate());
        }
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

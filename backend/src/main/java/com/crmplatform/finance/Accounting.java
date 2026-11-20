package com.crmplatform.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public read API of tenant business finance for higher modules (reporting). */
public interface Accounting {

    /** BR-094 rollup for a customer: contract = Σ project budgets; voided transactions excluded. */
    Totals ofContact(UUID contactId);

    /** The customer's most recent non-voided transactions (newest commitment date first). */
    List<TransactionOverview> recentOfContact(UUID contactId, int limit);

    /** Workspace money for the dashboard: received/paid committed in [from, to) and today's total outstanding. */
    PeriodSummary periodSummary(LocalDate from, LocalDate to);

    record PeriodSummary(BigDecimal received, BigDecimal paid, BigDecimal outstanding) {
    }

    record Totals(BigDecimal contract, BigDecimal received, BigDecimal actualCost, BigDecimal materialsCost,
            BigDecimal remaining,
            BigDecimal profitLoss, long transactionCount, boolean overpaid) {
    }

    record TransactionOverview(UUID id, UUID jobId, UUID projectId, String type, BigDecimal amount, String title,
            String method, LocalDate commitmentDate, LocalDate dueDate) {
    }
}

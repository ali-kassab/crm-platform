package com.crmplatform.finance.transactions.application;

import com.crmplatform.crm.Contacts;
import com.crmplatform.finance.Accounting;
import org.springframework.data.domain.PageRequest;
import com.crmplatform.finance.transactions.domain.Transaction.Type;
import com.crmplatform.finance.transactions.domain.TransactionRepository;
import com.crmplatform.operations.Jobs;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.work.Projects;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounting rollups (FR-FIN-04, BR-094): contract, received, actualCost, remaining = contract −
 * received, profitLoss = contract − actualCost. Voided transactions are excluded; transactions of
 * deleted jobs still count for their project and contact (EC-23).
 */
@Service
public class AccountingService implements Accounting {

    private final TransactionRepository transactions;
    private final Jobs jobs;
    private final Projects projects;
    private final Contacts contacts;

    public AccountingService(TransactionRepository transactions, Jobs jobs, Projects projects, Contacts contacts) {
        this.transactions = transactions;
        this.jobs = jobs;
        this.projects = projects;
        this.contacts = contacts;
    }

    @Transactional(readOnly = true)
    public Summary job(UUID jobId) {
        Jobs.JobSummary job = jobs.find(jobId).orElseThrow(() -> ApiException.notFound("Job"));
        return summarize(job.budget(), transactions.totalsByJob(jobId));
    }

    @Transactional(readOnly = true)
    public Summary project(UUID projectId) {
        Projects.ProjectSummary project = projects.find(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        return summarize(project.budget(), transactions.totalsByProject(projectId));
    }

    @Transactional(readOnly = true)
    public Summary contact(UUID contactId) {
        contacts.find(contactId).orElseThrow(() -> ApiException.notFound("Contact"));
        // contract = Σ project budgets + Σ budgets of the customer's standalone service jobs
        return summarize(projects.budgetTotalOfContact(contactId).add(jobs.standaloneBudgetTotalOfContact(contactId)),
                transactions.totalsByContact(contactId));
    }

    @Override
    @Transactional(readOnly = true)
    public Totals ofContact(UUID contactId) {
        Summary s = contact(contactId);
        return new Totals(s.contract(), s.received(), s.actualCost(), s.remaining(), s.profitLoss(),
                s.transactionCount(), s.overpaid());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionOverview> recentOfContact(UUID contactId, int limit) {
        return transactions.findByContactIdAndVoidedAtIsNullOrderByCommitmentDateDescCreatedAtDesc(contactId,
                        PageRequest.of(0, Math.max(1, Math.min(limit, 100)))).stream()
                .map(t -> new TransactionOverview(t.getId(), t.getJobId(), t.getProjectId(), t.getType().name(),
                        t.getAmount(), t.getTitle(), t.getMethod().name(), t.getCommitmentDate(), t.getDueDate()))
                .toList();
    }

    static Summary summarize(BigDecimal contract, List<Object[]> rows) {
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        long count = 0;
        for (Object[] row : rows) {
            BigDecimal sum = (BigDecimal) row[1];
            if (row[0] == Type.RECEIVED) {
                received = received.add(sum);
            } else {
                cost = cost.add(sum);
            }
            count += (Long) row[2];
        }
        BigDecimal c = contract == null ? BigDecimal.ZERO : contract;
        BigDecimal remaining = c.subtract(received);
        return new Summary(c, received, cost, remaining, c.subtract(cost), count, remaining.signum() < 0);
    }

    public record Summary(BigDecimal contract, BigDecimal received, BigDecimal actualCost, BigDecimal remaining,
            BigDecimal profitLoss, long transactionCount, boolean overpaid) {
    }
}

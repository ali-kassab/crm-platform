package com.crmplatform.reporting.application;

import com.crmplatform.crm.Contacts;
import com.crmplatform.finance.Accounting;
import com.crmplatform.operations.Jobs;
import com.crmplatform.operations.Warranties;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.work.Projects;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One customer's operational history across modules: projects, their jobs and warranties, and —
 * for callers allowed to see finance — the money summary and recent transactions. Read-only; built
 * only from the modules' public APIs, all of which are tenant-scoped.
 */
@Service
public class CustomerHistoryService {

    static final int RECENT_TRANSACTIONS = 20;

    private final Contacts contacts;
    private final Projects projects;
    private final Jobs jobs;
    private final Warranties warranties;
    private final Accounting accounting;

    public CustomerHistoryService(Contacts contacts, Projects projects, Jobs jobs, Warranties warranties,
            Accounting accounting) {
        this.contacts = contacts;
        this.projects = projects;
        this.jobs = jobs;
        this.warranties = warranties;
        this.accounting = accounting;
    }

    @Transactional(readOnly = true)
    public History of(UUID contactId, boolean includeFinance) {
        Contacts.ContactSummary contact = contacts.find(contactId).orElseThrow(() -> ApiException.notFound("Contact"));
        List<Projects.ProjectSummary> projectList = projects.ofContact(contactId);
        List<UUID> projectIds = projectList.stream().map(Projects.ProjectSummary::id).toList();
        return new History(contact, projectList, jobs.ofProjects(projectIds), warranties.ofProjects(projectIds),
                includeFinance ? accounting.ofContact(contactId) : null,
                includeFinance ? accounting.recentOfContact(contactId, RECENT_TRANSACTIONS) : null);
    }

    public record History(Contacts.ContactSummary contact, List<Projects.ProjectSummary> projects,
            List<Jobs.JobOverview> jobs, List<Warranties.WarrantyOverview> warranties, Accounting.Totals accounting,
            List<Accounting.TransactionOverview> recentTransactions) {
    }
}

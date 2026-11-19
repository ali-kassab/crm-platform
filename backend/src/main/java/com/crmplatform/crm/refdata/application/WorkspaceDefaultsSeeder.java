package com.crmplatform.crm.refdata.application;

import com.crmplatform.crm.refdata.domain.Category;
import com.crmplatform.crm.refdata.domain.Label.LabelValues;
import com.crmplatform.crm.refdata.domain.RefDataRepositories.CategoryRepository;
import com.crmplatform.crm.refdata.domain.RefDataRepositories.StatusRepository;
import com.crmplatform.crm.refdata.domain.Status;
import com.crmplatform.identity.WorkspaceCreated;
import com.crmplatform.shared.tenant.TenantContext;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds new workspaces with categories Client/Lead/Partner and statuses "New Client/Lead/Partner"
 * (BR-016). Runs synchronously right after sign-up commits, in a new transaction bound to the new
 * workspace, so the defaults exist before the sign-up response is returned.
 */
@Component
class WorkspaceDefaultsSeeder {

    private static final List<String> DEFAULTS = List.of("Client", "Lead", "Partner");

    private final CategoryRepository categories;
    private final StatusRepository statuses;
    private final TransactionTemplate tx;

    WorkspaceDefaultsSeeder(CategoryRepository categories, StatusRepository statuses, PlatformTransactionManager tm) {
        this.categories = categories;
        this.statuses = statuses;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener
    void on(WorkspaceCreated event) {
        TenantContext.runAs(event.workspaceId(), event.ownerUserId(), () -> tx.executeWithoutResult(status -> seed()));
    }

    private void seed() {
        if (categories.count() > 0) {
            return;
        }
        for (String name : DEFAULTS) {
            Category category = categories.save(new Category(name, null));
            statuses.save(new Status(new LabelValues("New " + name, category.getId(), null, "#000000", "#FFFFFF",
                    "check-check", null, null)));
        }
    }
}

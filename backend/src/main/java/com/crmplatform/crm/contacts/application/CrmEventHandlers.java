package com.crmplatform.crm.contacts.application;

import com.crmplatform.crm.LabelDeleted;
import com.crmplatform.identity.MemberDeactivated;
import com.crmplatform.shared.tenant.TenantWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to identity and reference-data changes (BR-024, EC-11). The workspace scope is set from
 * the event before the transaction opens ({@link TenantWork}), so a republished event can never
 * touch another workspace.
 */
@Component
class CrmEventHandlers {

    private final ContactService contacts;
    private final TenantWork tenantWork;

    CrmEventHandlers(ContactService contacts, TenantWork tenantWork) {
        this.contacts = contacts;
        this.tenantWork = tenantWork;
    }

    @TransactionalEventListener
    void on(MemberDeactivated event) {
        tenantWork.run(event.workspaceId(), null, () -> contacts.clearRepresentative(event.userId()));
    }

    @TransactionalEventListener
    void on(LabelDeleted event) {
        tenantWork.run(event.workspaceId(), null, () -> contacts.detachLabel(event.labelId(), event.tag()));
    }
}

package com.crmplatform.crm.contacts.application;

import com.crmplatform.crm.LabelDeleted;
import com.crmplatform.identity.MemberDeactivated;
import com.crmplatform.shared.tenant.TenantContext;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Reacts to identity and reference-data changes (BR-024, EC-11). */
@Component
class CrmEventHandlers {

    private final ContactService contacts;

    CrmEventHandlers(ContactService contacts) {
        this.contacts = contacts;
    }

    @ApplicationModuleListener
    void on(MemberDeactivated event) {
        TenantContext.runAs(event.workspaceId(), null, () -> contacts.clearRepresentative(event.userId()));
    }

    @ApplicationModuleListener
    void on(LabelDeleted event) {
        TenantContext.runAs(event.workspaceId(), null, () -> contacts.detachLabel(event.labelId(), event.tag()));
    }
}

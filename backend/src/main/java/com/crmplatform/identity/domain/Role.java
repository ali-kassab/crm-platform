package com.crmplatform.identity.domain;

import static com.crmplatform.shared.security.Permissions.AUDIT_READ;
import static com.crmplatform.shared.security.Permissions.AUTOMATION_MANAGE;
import static com.crmplatform.shared.security.Permissions.AUTOMATION_READ;
import static com.crmplatform.shared.security.Permissions.CRM_DELETE;
import static com.crmplatform.shared.security.Permissions.CRM_READ;
import static com.crmplatform.shared.security.Permissions.CRM_WRITE;
import static com.crmplatform.shared.security.Permissions.FILES_UPLOAD;
import static com.crmplatform.shared.security.Permissions.FINANCE_READ;
import static com.crmplatform.shared.security.Permissions.FINANCE_WRITE;
import static com.crmplatform.shared.security.Permissions.MEMBERS_MANAGE;
import static com.crmplatform.shared.security.Permissions.OPS_DELETE;
import static com.crmplatform.shared.security.Permissions.OPS_READ;
import static com.crmplatform.shared.security.Permissions.OPS_WRITE;
import static com.crmplatform.shared.security.Permissions.REFDATA_MANAGE;
import static com.crmplatform.shared.security.Permissions.REFDATA_READ;
import static com.crmplatform.shared.security.Permissions.REPORTS_READ;
import static com.crmplatform.shared.security.Permissions.TEMPLATES_MANAGE;
import static com.crmplatform.shared.security.Permissions.WORKSPACE_DELETE;
import static com.crmplatform.shared.security.Permissions.WORKSPACE_MANAGE;
import static com.crmplatform.shared.security.Permissions.WORK_DELETE;
import static com.crmplatform.shared.security.Permissions.WORK_READ;
import static com.crmplatform.shared.security.Permissions.WORK_WRITE;

import com.crmplatform.shared.security.Permissions;
import java.util.HashSet;
import java.util.Set;

/** Fixed workspace roles and their permissions (AUTHENTICATION_AND_AUTHORIZATION.md §2). */
public enum Role {
    VIEWER(Set.of(REFDATA_READ, CRM_READ, WORK_READ, OPS_READ)),
    MEMBER(union(VIEWER.permissions, Set.of(CRM_WRITE, CRM_DELETE, WORK_WRITE, OPS_WRITE, FINANCE_READ,
            AUTOMATION_READ, FILES_UPLOAD))),
    ADMIN(union(MEMBER.permissions, Set.of(WORKSPACE_MANAGE, MEMBERS_MANAGE, REFDATA_MANAGE, WORK_DELETE,
            TEMPLATES_MANAGE, OPS_DELETE, FINANCE_WRITE, AUTOMATION_MANAGE, AUDIT_READ, REPORTS_READ))),
    OWNER(union(ADMIN.permissions, Set.of(WORKSPACE_DELETE))),
    /**
     * D8: field technician — only the jobs and visits assigned to them (status, visits, photos and
     * reports). Customer name, phone and site come with the assigned work, so no CRM access.
     */
    TECHNICIAN(Set.of(REFDATA_READ, Permissions.OPS_ASSIGNED, FILES_UPLOAD)),
    /** D8: finance user — money in and out, read access to the work it belongs to. */
    FINANCE(Set.of(REFDATA_READ, CRM_READ, WORK_READ, OPS_READ, FINANCE_READ, FINANCE_WRITE, REPORTS_READ));

    private final Set<String> permissions;

    Role(Set<String> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<String> permissions() {
        return permissions;
    }

    /** Whether a member with this role may assign {@code target} to someone else. */
    public boolean canGrant(Role target) {
        return this == OWNER || (this == ADMIN && target != OWNER);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all;
    }
}

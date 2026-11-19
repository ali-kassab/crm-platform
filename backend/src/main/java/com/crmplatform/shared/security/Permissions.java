package com.crmplatform.shared.security;

/**
 * Permission authority names used in {@code @PreAuthorize("hasAuthority(...)")}. The role mapping
 * lives in the identity module (AUTHENTICATION_AND_AUTHORIZATION.md §2).
 */
public final class Permissions {

    public static final String WORKSPACE_MANAGE = "workspace:manage";
    public static final String WORKSPACE_DELETE = "workspace:delete";
    public static final String MEMBERS_MANAGE = "members:manage";
    public static final String REFDATA_READ = "refdata:read";
    public static final String REFDATA_MANAGE = "refdata:manage";
    public static final String CRM_READ = "crm:read";
    public static final String CRM_WRITE = "crm:write";
    public static final String CRM_DELETE = "crm:delete";
    public static final String WORK_READ = "work:read";
    public static final String WORK_WRITE = "work:write";
    public static final String WORK_DELETE = "work:delete";
    public static final String TEMPLATES_MANAGE = "templates:manage";
    public static final String OPS_READ = "ops:read";
    public static final String OPS_WRITE = "ops:write";
    public static final String OPS_DELETE = "ops:delete";
    public static final String FINANCE_READ = "finance:read";
    public static final String FINANCE_WRITE = "finance:write";
    public static final String AUTOMATION_READ = "automation:read";
    public static final String AUTOMATION_MANAGE = "automation:manage";
    public static final String AUDIT_READ = "audit:read";
    public static final String REPORTS_READ = "reports:read";
    public static final String FILES_UPLOAD = "files:upload";

    /** Granted only to platform super-admins; never derived from a workspace role. */
    public static final String PLATFORM_ADMIN = "platform:admin";

    private Permissions() {
    }
}

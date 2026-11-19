package com.crmplatform.operations.jobs.application;

import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.security.Permissions;
import java.util.UUID;

/**
 * Assignment scoping for field work (D8). A caller with {@code ops:read} sees all jobs and visits of
 * the workspace; a caller with only {@code ops:assigned} (technician) sees just the work assigned
 * to them. Background code without a signed-in user is never limited.
 */
public final class JobAccess {

    private JobAccess() {
    }

    public static boolean limitedToAssigned() {
        return CurrentUser.get().map(JobAccess::limited).orElse(false);
    }

    public static UUID currentUser() {
        return CurrentUser.id();
    }

    private static boolean limited(AuthenticatedUser user) {
        return !user.has(Permissions.OPS_READ) && user.has(Permissions.OPS_ASSIGNED);
    }
}

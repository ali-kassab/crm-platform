package com.crmplatform.shared.security;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The resolved caller of a request: identity from the access token, role and permissions
 * re-evaluated from the database for the token's active workspace.
 *
 * @param role        workspace role name, or {@code null} if the user is no longer a member
 * @param tokenId     access-token {@code jti}, used for the logout-all denylist
 */
public record AuthenticatedUser(
        UUID userId,
        UUID workspaceId,
        String role,
        Set<String> permissions,
        boolean superAdmin,
        String tokenId,
        Instant tokenExpiresAt) {

    public boolean isMember() {
        return role != null;
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public boolean isAdminOrOwner() {
        return "OWNER".equals(role) || "ADMIN".equals(role);
    }
}

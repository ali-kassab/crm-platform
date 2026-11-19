package com.crmplatform.identity;

import java.util.UUID;

/**
 * Published when a workspace is created at sign-up; other modules seed their defaults
 * (categories, statuses, board template) in response (AUTH_FLOWS F1).
 */
public record WorkspaceCreated(UUID workspaceId, UUID ownerUserId) {
}

package com.crmplatform.identity;

import java.util.UUID;

/** A member was deactivated or removed from a workspace (BR-024: clear contact representative). */
public record MemberDeactivated(UUID workspaceId, UUID userId) {
}

package com.crmplatform.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Public query API of the identity module for other modules (member validation and display names). */
public interface WorkspaceMembers {

    /** Whether the user is an active member of the active workspace. */
    boolean isActiveMember(UUID userId);

    Optional<MemberSummary> find(UUID userId);

    Map<UUID, MemberSummary> findAll(Collection<UUID> userIds);

    /** Active owners and admins of the active workspace (recipients of workspace-level reminders). */
    java.util.Set<UUID> managers();

    record MemberSummary(UUID userId, String firstName, String lastName, String email, String role) {
        public String fullName() {
            return firstName + " " + lastName;
        }
    }
}

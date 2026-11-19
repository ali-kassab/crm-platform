package com.crmplatform.work;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public API of projects and their teams for other modules (operations, finance, automation). */
public interface Projects {

    Optional<ProjectSummary> find(UUID projectId);

    Map<UUID, ProjectSummary> findAll(Collection<UUID> projectIds);

    /** Team members (team_member ids) of the project; used to validate job and task assignments. */
    Set<UUID> teamMemberIds(UUID projectId);

    /** User ids of the manager and every team member of the project. */
    List<TeamMemberSummary> team(UUID projectId);

    /** Adds the user to the project team if not yet a member; returns the team member id. */
    UUID ensureTeamMember(UUID projectId, UUID userId);

    void addTags(UUID projectId, Set<UUID> tagIds);

    void removeTags(UUID projectId, Set<UUID> tagIds);

    record ProjectSummary(UUID id, String name, String type, BigDecimal budget, UUID locationId, UUID contactId,
            UUID managerUserId, LocalDate startDate, LocalDate estimatedEndDate) {
    }

    record TeamMemberSummary(UUID teamMemberId, UUID userId, String name, String email, boolean manager) {
    }
}

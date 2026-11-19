package com.crmplatform.work.projects.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public final class ProjectRepositories {

    private ProjectRepositories() {
    }

    public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

        boolean existsByContactId(UUID contactId);

        boolean existsByLocationId(UUID locationId);

        @Query("select coalesce(sum(p.budget), 0) from Project p where p.contactId = :contactId")
        java.math.BigDecimal budgetTotalOfContact(UUID contactId);

        @Query("select p from Project p join p.tagIds t where t = :tagId")
        List<Project> findByTag(UUID tagId);
    }

    public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

        List<TeamMember> findByProjectId(UUID projectId);

        List<TeamMember> findByProjectIdIn(java.util.Collection<UUID> projectIds);

        Optional<TeamMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

        Optional<TeamMember> findByProjectIdAndManagerTrue(UUID projectId);

        List<TeamMember> findByProjectIdInAndManagerTrue(java.util.Collection<UUID> projectIds);
    }

    public interface TeamMemberNoteRepository extends JpaRepository<TeamMemberNote, UUID> {

        List<TeamMemberNote> findByTeamMemberIdOrderByCreatedAtDesc(UUID teamMemberId);
    }
}

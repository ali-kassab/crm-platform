package com.crmplatform.work.projects.application;

import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.work.WorkEvents.ProjectMembersChanged;
import com.crmplatform.work.projects.domain.Project;
import com.crmplatform.work.projects.domain.ProjectRepositories.ProjectRepository;
import com.crmplatform.work.projects.domain.ProjectRepositories.TeamMemberNoteRepository;
import com.crmplatform.work.projects.domain.ProjectRepositories.TeamMemberRepository;
import com.crmplatform.work.projects.domain.TeamMember;
import com.crmplatform.work.projects.domain.TeamMember.Responsibility;
import com.crmplatform.work.projects.domain.TeamMemberNote;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Individual team member management and notes (FR-WRK-06..08). */
@Service
public class TeamService {

    private final TeamMemberRepository teamMembers;
    private final TeamMemberNoteRepository notes;
    private final ProjectRepository projects;
    private final WorkspaceMembers members;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TeamService(TeamMemberRepository teamMembers, TeamMemberNoteRepository notes, ProjectRepository projects,
            WorkspaceMembers members, ApplicationEventPublisher events, Clock clock) {
        this.teamMembers = teamMembers;
        this.notes = notes;
        this.projects = projects;
        this.members = members;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public TeamMember add(UUID projectId, UUID userId, String position, Set<Responsibility> responsibilities) {
        Project project = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        if (!members.isActiveMember(userId)) {
            throw ApiException.referenceNotFound("userId");
        }
        if (teamMembers.findByProjectIdAndUserId(projectId, userId).isPresent()) {
            throw ApiException.conflict("ALREADY_IN_PROJECT", "This person is already on the project team");
        }
        TeamMember tm = new TeamMember(projectId, userId, position, false);
        tm.setResponsibilities(responsibilities);
        TeamMember saved = teamMembers.save(tm);
        events.publishEvent(new ProjectMembersChanged(TenantContext.requireWorkspaceId(), projectId, project.getName(),
                TenantContext.userId().orElse(null), Set.of(userId), Set.of(), null, null));
        return saved;
    }

    @Transactional
    public TeamMember update(UUID teamMemberId, String position, Set<Responsibility> responsibilities, boolean positionSent,
            boolean responsibilitiesSent) {
        TeamMember tm = get(teamMemberId);
        if (positionSent) {
            tm.setPosition(position);
        }
        if (responsibilitiesSent) {
            tm.setResponsibilities(responsibilities);
        }
        return tm;
    }

    @Transactional
    public void remove(UUID teamMemberId) {
        TeamMember tm = get(teamMemberId);
        if (tm.isManager()) {
            throw ApiException.conflict("IS_MANAGER", "Assign another project manager before removing this one");
        }
        tm.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        Project project = projects.findById(tm.getProjectId()).orElseThrow(() -> ApiException.notFound("Project"));
        events.publishEvent(new ProjectMembersChanged(TenantContext.requireWorkspaceId(), project.getId(),
                project.getName(), TenantContext.userId().orElse(null), Set.of(), Set.of(tm.getUserId()), null, null));
    }

    @Transactional(readOnly = true)
    public TeamMember get(UUID teamMemberId) {
        return teamMembers.findById(teamMemberId).orElseThrow(() -> ApiException.notFound("Team member"));
    }

    @Transactional(readOnly = true)
    public List<TeamMemberNote> notes(UUID teamMemberId) {
        get(teamMemberId);
        return notes.findByTeamMemberIdOrderByCreatedAtDesc(teamMemberId);
    }

    @Transactional
    public TeamMemberNote addNote(UUID teamMemberId, String text) {
        get(teamMemberId);
        return notes.save(new TeamMemberNote(teamMemberId, text));
    }

    /** Author or Admin+ (AZ-01, TS-WRK-12). */
    @Transactional
    public void deleteNote(UUID teamMemberId, UUID noteId) {
        TeamMemberNote note = notes.findById(noteId).filter(n -> n.getTeamMemberId().equals(teamMemberId))
                .orElseThrow(() -> ApiException.notFound("Note"));
        AuthenticatedUser user = CurrentUser.require();
        if (!Objects.equals(note.getCreatedBy(), user.userId()) && !user.isAdminOrOwner()) {
            throw ApiException.forbidden("Only the author or an admin can delete this note");
        }
        note.markDeleted(Instant.now(clock), user.userId());
    }
}

package com.crmplatform.work.projects.application;

import com.crmplatform.crm.Contacts;
import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.events.AutomationChain;
import com.crmplatform.shared.events.DomainEvents.ProjectDeleted;
import com.crmplatform.shared.events.DomainEvents.TaggableDeleted;
import com.crmplatform.shared.events.DomainEvents.TaggableType;
import com.crmplatform.shared.events.DomainEvents.TagsChanged;
import com.crmplatform.shared.persistence.Address;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.work.Boards;
import com.crmplatform.work.Boards.OwnerType;
import com.crmplatform.work.Projects;
import com.crmplatform.work.WorkEvents.ProjectDeletedNotice;
import com.crmplatform.work.WorkEvents.ProjectMembersChanged;
import com.crmplatform.work.WorkReferences.JobDirectory;
import com.crmplatform.work.projects.domain.Project;
import com.crmplatform.work.projects.domain.ProjectRepositories.ProjectRepository;
import com.crmplatform.work.projects.domain.ProjectRepositories.TeamMemberRepository;
import com.crmplatform.work.projects.domain.TeamMember;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Projects and their team composition (FR-WRK-01..06, BR-050..057). */
@Service
public class ProjectService implements Projects {

    private final ProjectRepository projects;
    private final TeamMemberRepository teamMembers;
    private final Contacts contacts;
    private final LabelCatalog labels;
    private final WorkspaceMembers members;
    private final Boards boards;
    private final Files files;
    private final ObjectProvider<JobDirectory> jobs;
    private final ApplicationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, TeamMemberRepository teamMembers, Contacts contacts,
            LabelCatalog labels, WorkspaceMembers members, Boards boards, Files files, ObjectProvider<JobDirectory> jobs,
            ApplicationEventPublisher events, AuditTrail audit, Clock clock) {
        this.projects = projects;
        this.teamMembers = teamMembers;
        this.contacts = contacts;
        this.labels = labels;
        this.members = members;
        this.boards = boards;
        this.files = files;
        this.jobs = jobs;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Page<Project> search(ProjectFilter filter, Pageable pageable) {
        if (filter.overBudget() == null) {
            return projects.findAll(ProjectSpecs.matching(filter, null), pageable);
        }
        // over-budget depends on job budgets owned by another module: resolve matching ids first
        List<Project> all = projects.findAll(ProjectSpecs.matching(filter, null));
        Map<UUID, BigDecimal> used = jobBudgets(all.stream().map(Project::getId).toList());
        Set<UUID> ids = all.stream()
                .filter(p -> remaining(p, used).signum() < 0 == filter.overBudget())
                .map(Project::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return projects.findAll(ProjectSpecs.matching(filter, ids), pageable);
    }

    @Transactional(readOnly = true)
    public Project get(UUID id) {
        return projects.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
    }

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> remainingBudgets(Collection<Project> list) {
        Map<UUID, BigDecimal> used = jobBudgets(list.stream().map(Project::getId).toList());
        Map<UUID, BigDecimal> remaining = new LinkedHashMap<>();
        list.forEach(p -> remaining.put(p.getId(), remaining(p, used)));
        return remaining;
    }

    @Transactional(readOnly = true)
    public List<TeamMember> teamOf(UUID projectId) {
        return teamMembers.findByProjectId(projectId);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public Project create(ProjectData d) {
        Contacts.LocationSummary location = contacts.location(d.locationId())
                .orElseThrow(() -> ApiException.referenceNotFound("locationId"));
        validateDates(d.startDate(), d.estimatedEndDate());
        Set<UUID> team = d.teamMemberUserIds() == null ? Set.of() : new LinkedHashSet<>(d.teamMemberUserIds());
        validateTeam(d.projectManagerUserId(), team);
        Set<UUID> tags = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
        labels.requireTags(tags, "tagIds");

        Project p = new Project(d.name(), d.description(), parseType(d.type()));
        p.setAddress(address(d.address()));
        p.setTotalArea(d.totalArea());
        p.setBudget(d.budget());
        p.setSchedule(d.startDate(), d.estimatedEndDate());
        p.setLocation(location.id(), location.contactId()); // BR-051
        p.replaceTags(tags);
        Project saved = projects.save(p);

        teamMembers.save(new TeamMember(saved.getId(), d.projectManagerUserId(), null, true));
        team.forEach(u -> teamMembers.save(new TeamMember(saved.getId(), u, null, false)));
        files.attach(d.documentFileIds(), FileKind.DOCUMENT, "PROJECT", saved.getId(), "documentFileIds");
        boards.createBoard(OwnerType.PROJECT, saved.getId(), saved.getName() + " board", d.boardTemplateId());

        Set<UUID> added = new LinkedHashSet<>(team);
        added.add(d.projectManagerUserId());
        events.publishEvent(new ProjectMembersChanged(TenantContext.requireWorkspaceId(), saved.getId(),
                saved.getName(), TenantContext.userId().orElse(null), added, Set.of(), d.projectManagerUserId(), null));
        publishTags(saved.getId(), tags, Set.of());
        audit.created("PROJECT", saved.getId(), saved.getId(), null, snapshot(saved));
        return saved;
    }

    @Transactional
    public Project update(UUID id, Patch<ProjectData> patch) {
        Project p = get(id);
        ProjectData d = patch.value();
        Map<String, Object> before = snapshot(p);
        Set<UUID> previousTags = p.getTagIds();

        patch.ifPresent("name", d.name(), v -> p.setName(required(v, "name")));
        patch.ifPresent("description", d.description(), v -> p.setDescription(required(v, "description")));
        patch.ifPresent("type", d.type(), v -> p.setType(parseType(required(v, "type"))));
        patch.ifPresent("address", d.address(), v -> p.setAddress(address(required(v, "address"))));
        patch.ifPresent("totalArea", d.totalArea(), v -> p.setTotalArea(required(v, "totalArea")));
        patch.ifPresent("budget", d.budget(), v -> p.setBudget(required(v, "budget")));
        if (patch.has("startDate") || patch.has("estimatedEndDate")) {
            LocalDate start = patch.has("startDate") ? d.startDate() : p.getStartDate();
            LocalDate end = patch.has("estimatedEndDate") ? d.estimatedEndDate() : p.getEstimatedEndDate();
            validateDates(start, end);
            p.setSchedule(start, end);
        }
        if (patch.has("locationId")) {
            Contacts.LocationSummary location = contacts.location(d.locationId())
                    .orElseThrow(() -> ApiException.referenceNotFound("locationId"));
            p.setLocation(location.id(), location.contactId());
        }
        if (patch.has("tagIds")) {
            Set<UUID> tags = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
            labels.requireTags(tags, "tagIds");
            p.replaceTags(tags);
        }
        if (patch.has("documentFileIds")) {
            List<UUID> docs = d.documentFileIds() == null ? List.of() : d.documentFileIds();
            files.attach(docs, FileKind.DOCUMENT, "PROJECT", id, "documentFileIds");
            files.detachAllExcept("PROJECT", id, docs);
        }
        if (patch.has("projectManagerUserId") || patch.has("teamMemberUserIds")) {
            reconcileTeam(p, patch.has("projectManagerUserId") ? d.projectManagerUserId() : null,
                    patch.has("teamMemberUserIds") ? d.teamMemberUserIds() : null);
        }
        Set<UUID> addedTags = minus(p.getTagIds(), previousTags);
        Set<UUID> removedTags = minus(previousTags, p.getTagIds());
        publishTags(id, addedTags, removedTags);
        audit.updated("PROJECT", id, id, null, before, snapshot(p));
        return p;
    }

    /** Soft-deletes the project with its team and board; jobs, notes, warranties cascade via event (BR-056). */
    @Transactional
    public void delete(UUID id) {
        Project p = get(id);
        Instant now = Instant.now(clock);
        UUID actor = TenantContext.userId().orElse(null);
        List<TeamMember> team = teamMembers.findByProjectId(id);
        Set<UUID> memberUsers = team.stream().map(TeamMember::getUserId).collect(Collectors.toSet());
        team.forEach(t -> t.markDeleted(now, actor));
        boards.deleteBoardOf(OwnerType.PROJECT, id);
        p.markDeleted(now, actor);
        UUID workspaceId = TenantContext.requireWorkspaceId();
        events.publishEvent(new ProjectDeleted(workspaceId, id));
        events.publishEvent(new TaggableDeleted(workspaceId, TaggableType.PROJECT, id));
        events.publishEvent(new ProjectDeletedNotice(workspaceId, id, p.getName(), actor, memberUsers));
        audit.deleted("PROJECT", id, id, null);
    }

    // ------------------------------------------------------------------ team

    /**
     * Applies a new manager and/or team list (FR-WRK-04). A team member promoted to manager
     * leaves the team list (EC-14); removed members are soft-deleted.
     */
    private void reconcileTeam(Project p, UUID managerUserId, Collection<UUID> teamUserIds) {
        List<TeamMember> current = teamMembers.findByProjectId(p.getId());
        TeamMember currentManager = current.stream().filter(TeamMember::isManager).findFirst().orElse(null);
        UUID newManager = managerUserId != null ? managerUserId
                : currentManager == null ? null : currentManager.getUserId();
        Set<UUID> desiredTeam = teamUserIds == null
                ? current.stream().filter(t -> !t.isManager()).map(TeamMember::getUserId)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                : new LinkedHashSet<>(teamUserIds);
        desiredTeam.remove(newManager);
        if (teamUserIds != null && managerUserId != null && teamUserIds.contains(managerUserId)) {
            throw ApiException.badRequest("MANAGER_IN_TEAM", "The project manager cannot also be a team member");
        }
        validateTeam(newManager, desiredTeam);

        Set<UUID> before = current.stream().map(TeamMember::getUserId).collect(Collectors.toSet());
        Instant now = Instant.now(clock);
        UUID actor = TenantContext.userId().orElse(null);
        UUID previousManager = currentManager == null ? null : currentManager.getUserId();

        // pass 1: removals and demotions, flushed first so the single-manager index is never violated
        for (TeamMember tm : current) {
            boolean keep = tm.getUserId().equals(newManager) || desiredTeam.contains(tm.getUserId());
            if (!keep) {
                tm.markDeleted(now, actor);
            } else if (tm.isManager() && !tm.getUserId().equals(newManager)) {
                tm.demoteFromManager();
            }
        }
        teamMembers.flush();
        // pass 2: promotion
        current.stream().filter(tm -> !tm.isDeleted() && tm.getUserId().equals(newManager))
                .forEach(TeamMember::promoteToManager);
        teamMembers.flush();
        Set<UUID> existing = current.stream().filter(tm -> !tm.isDeleted()).map(TeamMember::getUserId)
                .collect(Collectors.toSet());
        if (!existing.contains(newManager)) {
            teamMembers.save(new TeamMember(p.getId(), newManager, null, true));
        }
        desiredTeam.stream().filter(u -> !existing.contains(u))
                .forEach(u -> teamMembers.save(new TeamMember(p.getId(), u, null, false)));

        Set<UUID> after = new HashSet<>(desiredTeam);
        after.add(newManager);
        Set<UUID> added = minus(after, before);
        Set<UUID> removed = minus(before, after);
        boolean managerChanged = !Objects.equals(previousManager, newManager);
        if (!added.isEmpty() || !removed.isEmpty() || managerChanged) {
            events.publishEvent(new ProjectMembersChanged(TenantContext.requireWorkspaceId(), p.getId(), p.getName(),
                    actor, added, removed, managerChanged ? newManager : null, managerChanged ? previousManager : null));
        }
    }

    private void validateTeam(UUID managerUserId, Set<UUID> team) {
        if (managerUserId == null || !members.isActiveMember(managerUserId)) {
            throw ApiException.referenceNotFound("projectManagerUserId");
        }
        if (team.contains(managerUserId)) {
            throw ApiException.badRequest("MANAGER_IN_TEAM", "The project manager cannot also be a team member");
        }
        if (team.size() > 50) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A project team can have at most 50 members");
        }
        for (UUID u : team) {
            if (!members.isActiveMember(u)) {
                throw ApiException.referenceNotFound("teamMemberUserIds");
            }
        }
    }

    // ------------------------------------------------------------------ Projects (public API)

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSummary> find(UUID projectId) {
        return projectId == null ? Optional.empty() : projects.findById(projectId).map(this::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ProjectSummary> findAll(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return projects.findAllById(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(Project::getId, this::summary));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> teamMemberIds(UUID projectId) {
        return teamMembers.findByProjectId(projectId).stream().map(TeamMember::getId).collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberSummary> team(UUID projectId) {
        List<TeamMember> list = teamMembers.findByProjectId(projectId);
        var people = members.findAll(list.stream().map(TeamMember::getUserId).toList());
        List<TeamMemberSummary> result = new ArrayList<>();
        for (TeamMember tm : list) {
            var person = people.get(tm.getUserId());
            result.add(new TeamMemberSummary(tm.getId(), tm.getUserId(), person == null ? null : person.fullName(),
                    person == null ? null : person.email(), tm.isManager()));
        }
        return result;
    }

    @Override
    @Transactional
    public UUID ensureTeamMember(UUID projectId, UUID userId) {
        get(projectId);
        return teamMembers.findByProjectIdAndUserId(projectId, userId).map(TeamMember::getId).orElseGet(() -> {
            if (!members.isActiveMember(userId)) {
                throw ApiException.referenceNotFound("userId");
            }
            return teamMembers.save(new TeamMember(projectId, userId, null, false)).getId();
        });
    }

    @Override
    @Transactional
    public void addTags(UUID projectId, Set<UUID> tagIds) {
        Project p = get(projectId);
        labels.requireTags(tagIds, "tagIds");
        Set<UUID> next = new HashSet<>(p.getTagIds());
        next.addAll(tagIds);
        Set<UUID> added = minus(next, p.getTagIds());
        p.replaceTags(next);
        publishTags(projectId, added, Set.of());
    }

    @Override
    @Transactional
    public void removeTags(UUID projectId, Set<UUID> tagIds) {
        Project p = get(projectId);
        Set<UUID> next = new HashSet<>(p.getTagIds());
        next.removeAll(tagIds);
        Set<UUID> removed = minus(p.getTagIds(), next);
        p.replaceTags(next);
        publishTags(projectId, Set.of(), removed);
    }

    @Transactional
    public void detachTag(UUID tagId) {
        projects.findByTag(tagId).forEach(p -> {
            Set<UUID> next = new HashSet<>(p.getTagIds());
            next.remove(tagId);
            p.replaceTags(next);
        });
    }

    // ------------------------------------------------------------------ helpers

    private ProjectSummary summary(Project p) {
        UUID manager = teamMembers.findByProjectIdAndManagerTrue(p.getId()).map(TeamMember::getUserId).orElse(null);
        return new ProjectSummary(p.getId(), p.getName(), p.getType().name(), p.getBudget(), p.getLocationId(),
                p.getContactId(), manager, p.getStartDate(), p.getEstimatedEndDate());
    }

    private Map<UUID, BigDecimal> jobBudgets(List<UUID> projectIds) {
        JobDirectory directory = jobs.getIfAvailable();
        return directory == null || projectIds.isEmpty() ? Map.of() : directory.jobBudgetTotals(projectIds);
    }

    /** BR-055: remaining = budget − Σ active job budgets. */
    static BigDecimal remaining(Project p, Map<UUID, BigDecimal> used) {
        return p.getBudget().subtract(used.getOrDefault(p.getId(), BigDecimal.ZERO));
    }

    private void publishTags(UUID projectId, Set<UUID> added, Set<UUID> removed) {
        if (!added.isEmpty() || !removed.isEmpty()) {
            events.publishEvent(new TagsChanged(TenantContext.requireWorkspaceId(), TaggableType.PROJECT, projectId,
                    added, removed, AutomationChain.nextDepth()));
        }
    }

    private static Address address(AddressData a) {
        if (a == null || isBlank(a.city()) || isBlank(a.region()) || isBlank(a.postalCode()) || isBlank(a.country())) {
            throw ApiException.badRequest("VALIDATION_ERROR", "address.city, region, postalCode and country are required");
        }
        return new Address(a.line1(), a.line2(), a.city(), a.region(), a.postalCode(), a.country());
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "startDate and estimatedEndDate are required");
        }
        if (end.isBefore(start)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "estimatedEndDate must be on or after startDate");
        }
    }

    private static Project.Type parseType(String type) {
        try {
            return type == null ? Project.Type.COMMERCIAL : Project.Type.valueOf(type);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown project type");
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw ApiException.badRequest("VALIDATION_ERROR", field + " is required");
        }
        return value;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static Set<UUID> minus(Set<UUID> a, Set<UUID> b) {
        Set<UUID> r = new HashSet<>(a);
        r.removeAll(b);
        return r;
    }

    private static Map<String, Object> snapshot(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("type", p.getType().name());
        m.put("budget", p.getBudget() == null ? null : p.getBudget().toPlainString());
        m.put("startDate", String.valueOf(p.getStartDate()));
        m.put("estimatedEndDate", String.valueOf(p.getEstimatedEndDate()));
        m.put("locationId", p.getLocationId());
        m.put("tagIds", p.getTagIds().stream().map(UUID::toString).sorted().toList());
        return m;
    }

    public record AddressData(String line1, String line2, String city, String region, String postalCode,
            String country) {
    }

    public record ProjectData(String name, String description, String type, AddressData address, BigDecimal totalArea,
            BigDecimal budget, LocalDate startDate, LocalDate estimatedEndDate, UUID locationId,
            UUID projectManagerUserId, List<UUID> teamMemberUserIds, UUID boardTemplateId, List<UUID> documentFileIds,
            Set<UUID> tagIds) {
    }

    public record ProjectFilter(String type, UUID tagId, UUID locationId, UUID contactId, LocalDate startFrom,
            LocalDate startTo, Boolean overBudget, String q) {
    }
}

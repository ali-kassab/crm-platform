package com.crmplatform.operations.jobs.application;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.files.Files.FileSummary;
import com.crmplatform.operations.Jobs;
import com.crmplatform.operations.jobs.domain.Job;
import com.crmplatform.operations.jobs.domain.JobRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.events.AutomationChain;
import com.crmplatform.shared.events.DomainEvents.JobDeleted;
import com.crmplatform.shared.events.DomainEvents.TaggableDeleted;
import com.crmplatform.shared.events.DomainEvents.TaggableType;
import com.crmplatform.shared.events.DomainEvents.TagsChanged;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.work.Boards;
import com.crmplatform.work.Boards.OwnerType;
import com.crmplatform.work.Projects;
import com.crmplatform.work.WorkReferences.JobDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Jobs inside projects (FR-OPS-01..05, BR-055, BR-060..063). */
@Service
public class JobService implements JobDirectory, Jobs {

    static final String OWNER = "JOB";

    private final JobRepository jobs;
    private final Projects projects;
    private final Boards boards;
    private final LabelCatalog labels;
    private final Files files;
    private final ApplicationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;

    public JobService(JobRepository jobs, Projects projects, Boards boards, LabelCatalog labels, Files files,
            ApplicationEventPublisher events, AuditTrail audit, Clock clock) {
        this.jobs = jobs;
        this.projects = projects;
        this.boards = boards;
        this.labels = labels;
        this.files = files;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Page<Job> search(JobFilter filter, Pageable pageable) {
        return jobs.findAll(JobSpecs.matching(filter), pageable);
    }

    @Transactional(readOnly = true)
    public Job get(UUID id) {
        return jobs.findById(id).orElseThrow(() -> ApiException.notFound("Job"));
    }

    @Transactional(readOnly = true)
    public List<FileSummary> filesOf(UUID jobId) {
        return files.ownedBy(OWNER, jobId);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public Job create(JobData d) {
        projects.find(d.projectId()).orElseThrow(() -> ApiException.referenceNotFound("projectId"));
        validateDates(d.startDate(), d.estimatedEndDate());
        Team team = resolveTeam(d.projectId(), d.teamManagerUserId(), d.teamMemberUserIds());
        Set<UUID> tags = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
        labels.requireTags(tags, "tagIds");

        Job job = new Job(d.projectId(), d.title(), d.description());
        job.setSchedule(d.startDate(), d.estimatedEndDate());
        job.setBudget(required(d.budget(), "budget"));
        job.setTeam(team.manager(), team.members());
        job.replaceTags(tags);
        Job saved = jobs.save(job);

        files.attach(d.mediaFileIds(), FileKind.IMAGE, OWNER, saved.getId(), "mediaFileIds");
        files.attach(d.documentFileIds(), FileKind.DOCUMENT, OWNER, saved.getId(), "documentFileIds");
        boards.createBoard(OwnerType.JOB, saved.getId(), saved.getTitle() + " board", d.boardTemplateId()); // BR-062
        publishTags(saved.getId(), tags, Set.of());
        audit.created("JOB", saved.getId(), saved.getProjectId(), saved.getId(), snapshot(saved));
        return saved;
    }

    @Transactional
    public Job update(UUID id, Patch<JobData> patch) {
        Job job = get(id);
        JobData d = patch.value();
        Map<String, Object> before = snapshot(job);
        Set<UUID> previousTags = job.getTagIds();

        patch.ifPresent("title", d.title(), v -> job.setTitle(required(v, "title")));
        patch.ifPresent("description", d.description(), v -> job.setDescription(required(v, "description")));
        patch.ifPresent("budget", d.budget(), v -> job.setBudget(required(v, "budget")));
        if (patch.has("startDate") || patch.has("estimatedEndDate")) {
            LocalDate start = patch.has("startDate") ? d.startDate() : job.getStartDate();
            LocalDate end = patch.has("estimatedEndDate") ? d.estimatedEndDate() : job.getEstimatedEndDate();
            validateDates(start, end);
            job.setSchedule(start, end);
        }
        if (patch.has("teamManagerUserId") || patch.has("teamMemberUserIds")) {
            Team current = currentTeamAsUsers(job);
            UUID manager = patch.has("teamManagerUserId") ? d.teamManagerUserId() : current.manager();
            Collection<UUID> members = patch.has("teamMemberUserIds") ? d.teamMemberUserIds() : current.members();
            if (!patch.has("teamMemberUserIds") && manager != null) {
                members = members.stream().filter(u -> !u.equals(manager)).toList(); // promoted member leaves the list
            }
            Team team = resolveTeam(job.getProjectId(), manager, members);
            job.setTeam(team.manager(), team.members());
        }
        if (patch.has("tagIds")) {
            Set<UUID> tags = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
            labels.requireTags(tags, "tagIds");
            job.replaceTags(tags);
        }
        if (patch.has("mediaFileIds") || patch.has("documentFileIds")) {
            List<FileSummary> owned = files.ownedBy(OWNER, id);
            List<UUID> media = patch.has("mediaFileIds") ? nonNull(d.mediaFileIds()) : idsOf(owned, FileKind.IMAGE);
            List<UUID> docs = patch.has("documentFileIds") ? nonNull(d.documentFileIds())
                    : idsOf(owned, FileKind.DOCUMENT);
            files.attach(media, FileKind.IMAGE, OWNER, id, "mediaFileIds");
            files.attach(docs, FileKind.DOCUMENT, OWNER, id, "documentFileIds");
            Set<UUID> keep = new HashSet<>(media);
            keep.addAll(docs);
            files.detachAllExcept(OWNER, id, keep); // BR-063: removed files are deleted
        }
        jobs.saveAndFlush(job);
        publishTags(id, minus(job.getTagIds(), previousTags), minus(previousTags, job.getTagIds()));
        audit.updated("JOB", id, job.getProjectId(), id, before, snapshot(job));
        return job;
    }

    /** Soft-deletes the job with its board and tasks; its budget stops counting (EC-23). */
    @Transactional
    public void delete(UUID id) {
        Job job = get(id);
        UUID actor = TenantContext.userId().orElse(null);
        boards.deleteBoardOf(OwnerType.JOB, id);
        job.markDeleted(Instant.now(clock), actor);
        UUID workspaceId = TenantContext.requireWorkspaceId();
        events.publishEvent(new JobDeleted(workspaceId, id, job.getProjectId()));
        events.publishEvent(new TaggableDeleted(workspaceId, TaggableType.JOB, id));
        audit.deleted("JOB", id, job.getProjectId(), id);
    }

    @Transactional
    public void deleteAllOfProject(UUID projectId) {
        jobs.findByProjectId(projectId).forEach(j -> delete(j.getId()));
    }

    @Transactional
    public List<FileSummary> addFiles(UUID id, Collection<UUID> fileIds) {
        get(id);
        files.attach(fileIds, null, OWNER, id, "fileIds");
        return files.ownedBy(OWNER, id);
    }

    /** BR-063: removing a file from a job deletes the stored object. */
    @Transactional
    public void removeFile(UUID id, UUID fileId) {
        get(id);
        boolean owned = files.ownedBy(OWNER, id).stream().anyMatch(f -> f.id().equals(fileId));
        if (!owned) {
            throw ApiException.notFound("File");
        }
        files.delete(fileId);
    }

    @Transactional
    public void detachTag(UUID tagId) {
        jobs.findByTag(tagId).forEach(j -> {
            Set<UUID> next = new HashSet<>(j.getTagIds());
            next.remove(tagId);
            j.replaceTags(next);
        });
    }

    // ------------------------------------------------------------------ JobDirectory / Jobs

    @Override
    @Transactional(readOnly = true)
    public Optional<JobRef> job(UUID jobId) {
        return jobId == null ? Optional.empty()
                : jobs.findById(jobId).map(j -> new JobRef(j.getId(), j.getProjectId(), j.getTitle()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> jobBudgetTotals(Collection<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> totals = new HashMap<>();
        for (Object[] row : jobs.budgetTotals(new HashSet<>(projectIds))) {
            totals.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return totals;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobSummary> find(UUID jobId) {
        return jobId == null ? Optional.empty()
                : jobs.findById(jobId).map(j -> new JobSummary(j.getId(), j.getProjectId(), j.getTitle(), j.getBudget()));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Maps user ids to project team members, adding users to the project team when needed
     * (BR-060). The manager can't also be a member (BR-061).
     */
    private Team resolveTeam(UUID projectId, UUID managerUserId, Collection<UUID> memberUserIds) {
        Set<UUID> users = memberUserIds == null ? Set.of() : new LinkedHashSet<>(memberUserIds);
        if (managerUserId != null && users.contains(managerUserId)) {
            throw ApiException.badRequest("MANAGER_IN_TEAM", "The job manager cannot also be a job team member");
        }
        if (users.size() > 50) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A job team can have at most 50 members");
        }
        UUID manager = managerUserId == null ? null : projects.ensureTeamMember(projectId, managerUserId);
        Set<UUID> members = new LinkedHashSet<>();
        users.forEach(u -> members.add(projects.ensureTeamMember(projectId, u)));
        return new Team(manager, members);
    }

    private Team currentTeamAsUsers(Job job) {
        Map<UUID, UUID> userByMember = projects.team(job.getProjectId()).stream()
                .collect(Collectors.toMap(Projects.TeamMemberSummary::teamMemberId, Projects.TeamMemberSummary::userId));
        UUID manager = job.getTeamManagerMemberId() == null ? null : userByMember.get(job.getTeamManagerMemberId());
        Set<UUID> members = job.getMemberIds().stream().map(userByMember::get).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new Team(manager, members);
    }

    private void publishTags(UUID jobId, Set<UUID> added, Set<UUID> removed) {
        if (!added.isEmpty() || !removed.isEmpty()) {
            events.publishEvent(new TagsChanged(TenantContext.requireWorkspaceId(), TaggableType.JOB, jobId, added,
                    removed, AutomationChain.nextDepth()));
        }
    }

    private static List<UUID> idsOf(List<FileSummary> owned, FileKind kind) {
        return owned.stream().filter(f -> f.kind() == kind).map(FileSummary::id).toList();
    }

    private static List<UUID> nonNull(List<UUID> ids) {
        return ids == null ? List.of() : ids;
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "startDate and estimatedEndDate are required");
        }
        if (end.isBefore(start)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "estimatedEndDate must be on or after startDate");
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw ApiException.badRequest("VALIDATION_ERROR", field + " is required");
        }
        return value;
    }

    private static Set<UUID> minus(Set<UUID> a, Set<UUID> b) {
        Set<UUID> r = new HashSet<>(a);
        r.removeAll(b);
        return r;
    }

    private static Map<String, Object> snapshot(Job j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", j.getTitle());
        m.put("budget", j.getBudget() == null ? null
                : j.getBudget().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        m.put("startDate", String.valueOf(j.getStartDate()));
        m.put("estimatedEndDate", String.valueOf(j.getEstimatedEndDate()));
        m.put("teamManagerMemberId", j.getTeamManagerMemberId());
        m.put("tagIds", j.getTagIds().stream().map(UUID::toString).sorted().toList());
        return m;
    }

    private record Team(UUID manager, Set<UUID> members) {
    }

    public record JobData(UUID projectId, String title, String description, LocalDate startDate,
            LocalDate estimatedEndDate, BigDecimal budget, UUID teamManagerUserId, List<UUID> teamMemberUserIds,
            UUID boardTemplateId, List<UUID> mediaFileIds, List<UUID> documentFileIds, Set<UUID> tagIds) {
    }

    public record JobFilter(UUID projectId, UUID tagId, LocalDate startFrom, LocalDate startTo, String q) {
    }
}

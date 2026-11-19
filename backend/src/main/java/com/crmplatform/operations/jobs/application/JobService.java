package com.crmplatform.operations.jobs.application;

import com.crmplatform.crm.Contacts;
import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.files.Files.FileSummary;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.operations.Jobs;
import com.crmplatform.operations.OperationsEvents.JobAssigned;
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

/**
 * Jobs / work orders (FR-OPS-01..05, directive §5C/§5D): inside a project (fit-out) or standalone
 * for a customer and site (service). Technicians ({@code ops:assigned}) only ever see and change jobs
 * assigned to them — enforced here, not in the UI.
 */
@Service
public class JobService implements JobDirectory, Jobs {

    static final String OWNER = "JOB";

    private final JobRepository jobs;
    private final Projects projects;
    private final Contacts contacts;
    private final WorkspaceMembers members;
    private final Boards boards;
    private final LabelCatalog labels;
    private final Files files;
    private final ApplicationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;
    private final com.crmplatform.operations.jobs.domain.JobMaterialRepository materials;
    private final SlaPolicy slaPolicy;

    public JobService(JobRepository jobs, Projects projects, Contacts contacts, WorkspaceMembers members, Boards boards,
            LabelCatalog labels, Files files, ApplicationEventPublisher events, AuditTrail audit, Clock clock,
            com.crmplatform.operations.jobs.domain.JobMaterialRepository materials, SlaPolicy slaPolicy,
            org.springframework.beans.factory.ObjectProvider<com.crmplatform.shared.limits.UsageGuard> usageGuard) {
        this.usageGuard = usageGuard;
        this.materials = materials;
        this.slaPolicy = slaPolicy;
        this.jobs = jobs;
        this.projects = projects;
        this.contacts = contacts;
        this.members = members;
        this.boards = boards;
        this.labels = labels;
        this.files = files;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    /** Callers limited to their own work get their assigned jobs only, whatever the filter says. */
    @Transactional(readOnly = true)
    public Page<Job> search(JobFilter filter, Pageable pageable) {
        JobFilter effective = JobAccess.limitedToAssigned()
                ? filter.withAssignee(JobAccess.currentUser()) : filter;
        return jobs.findAll(JobSpecs.matching(effective, Instant.now(clock)), pageable);
    }

    /** Throws 404 (not 403) for a job the caller may not see, so ids of others' work don't leak. */
    @Transactional(readOnly = true)
    public Job get(UUID id) {
        Job job = jobs.findById(id).orElseThrow(() -> ApiException.notFound("Job"));
        if (JobAccess.limitedToAssigned() && !job.isAssignedTo(JobAccess.currentUser())) {
            throw ApiException.notFound("Job");
        }
        return job;
    }

    @Transactional(readOnly = true)
    public List<FileSummary> filesOf(UUID jobId) {
        return files.ownedBy(OWNER, jobId);
    }

    private org.springframework.beans.factory.ObjectProvider<com.crmplatform.shared.limits.UsageGuard> usageGuard;

    /** Plan limits (billing); a no-op when no guard is configured. */
    private void requireUsage(UUID workspaceId, com.crmplatform.shared.limits.UsageGuard.Usage usage, long amount) {
        if (usageGuard != null) {
            usageGuard.ifAvailable(g -> g.require(workspaceId, usage, amount));
        }
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public Job create(JobData d) {
        requireUsage(TenantContext.requireWorkspaceId(), com.crmplatform.shared.limits.UsageGuard.Usage.JOBS_PER_MONTH, 1);
        return createJob(d);
    }

    private Job createJob(JobData d) {
        Site site = resolveSite(d.projectId(), d.contactId(), d.locationId());
        validateDates(d.startDate(), d.estimatedEndDate());
        Team team = d.projectId() == null ? requireNoTeam(d) : resolveTeam(d.projectId(), d.teamManagerUserId(),
                d.teamMemberUserIds());
        Set<UUID> tags = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
        labels.requireTags(tags, "tagIds");

        Job job = new Job(d.projectId(), site.contactId(), site.locationId(), d.title(), d.description());
        job.classify(parseType(d.type()), parsePriority(d.priority()));
        job.setReportedIssue(d.reportedIssue());
        job.setInternalNotes(d.internalNotes());
        job.setSchedule(d.startDate(), d.estimatedEndDate());
        job.setBudget(d.budget() == null ? BigDecimal.ZERO : d.budget());
        job.setTeam(team.manager(), team.members());
        job.assign(validAssignees(d.assigneeUserIds()));
        job.replaceTags(tags);
        job.setResponseDueAt(responseDeadline(d.responseHours(), job, Instant.now(clock)));
        Job saved = jobs.save(job);

        files.attach(d.mediaFileIds(), FileKind.IMAGE, OWNER, saved.getId(), "mediaFileIds");
        files.attach(d.documentFileIds(), FileKind.DOCUMENT, OWNER, saved.getId(), "documentFileIds");
        boards.createBoard(OwnerType.JOB, saved.getId(), saved.getTitle() + " board", d.boardTemplateId()); // BR-062
        publishTags(saved.getId(), tags, Set.of());
        notifyAssigned(saved, Set.of());
        audit.created("JOB", saved.getId(), saved.getProjectId(), saved.getId(), snapshot(saved));
        return saved;
    }

    @Transactional
    public Job update(UUID id, Patch<JobData> patch) {
        Job job = get(id);
        JobData d = patch.value();
        Map<String, Object> before = snapshot(job);
        Set<UUID> previousTags = job.getTagIds();
        Set<UUID> previousAssignees = Set.copyOf(job.getAssigneeUserIds());

        patch.ifPresent("title", d.title(), v -> job.setTitle(required(v, "title")));
        patch.ifPresent("description", d.description(), job::setDescription);
        patch.ifPresent("reportedIssue", d.reportedIssue(), job::setReportedIssue);
        patch.ifPresent("internalNotes", d.internalNotes(), job::setInternalNotes);
        patch.ifPresent("budget", d.budget(), v -> job.setBudget(v == null ? BigDecimal.ZERO : v));
        if (patch.has("type") || patch.has("priority")) {
            job.classify(patch.has("type") ? parseType(required(d.type(), "type")) : job.getType(),
                    patch.has("priority") ? parsePriority(required(d.priority(), "priority")) : job.getPriority());
        }
        if (patch.has("startDate") || patch.has("estimatedEndDate")) {
            LocalDate start = patch.has("startDate") ? d.startDate() : job.getStartDate();
            LocalDate end = patch.has("estimatedEndDate") ? d.estimatedEndDate() : job.getEstimatedEndDate();
            validateDates(start, end);
            job.setSchedule(start, end);
        }
        if (patch.has("contactId") || patch.has("locationId")) {
            if (job.getProjectId() != null && patch.has("contactId")) {
                throw ApiException.badRequest("VALIDATION_ERROR", "A project job keeps the project's customer");
            }
            Site site = resolveSite(job.getProjectId(),
                    patch.has("contactId") ? d.contactId() : job.getContactId(),
                    patch.has("locationId") ? d.locationId() : job.getLocationId());
            job.setSite(site.contactId(), site.locationId());
        }
        if (patch.has("teamManagerUserId") || patch.has("teamMemberUserIds")) {
            if (job.getProjectId() == null) {
                throw ApiException.badRequest("TEAM_NEEDS_PROJECT",
                        "Project teams only apply to project jobs; assign technicians instead");
            }
            Team current = currentTeamAsUsers(job);
            UUID manager = patch.has("teamManagerUserId") ? d.teamManagerUserId() : current.manager();
            Collection<UUID> teamUsers = patch.has("teamMemberUserIds") ? d.teamMemberUserIds() : current.members();
            if (!patch.has("teamMemberUserIds") && manager != null) {
                teamUsers = teamUsers.stream().filter(u -> !u.equals(manager)).toList(); // promoted member leaves the list
            }
            Team team = resolveTeam(job.getProjectId(), manager, teamUsers);
            job.setTeam(team.manager(), team.members());
        }
        if (patch.has("assigneeUserIds")) {
            job.assign(validAssignees(d.assigneeUserIds()));
        }
        if (patch.has("responseHours")) {
            job.setResponseDueAt(d.responseHours() == null ? null
                    : job.getCreatedAt().plus(java.time.Duration.ofHours(d.responseHours())));
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
        notifyAssigned(job, previousAssignees);
        audit.updated("JOB", id, job.getProjectId(), id, before, snapshot(job));
        return job;
    }

    /** Lifecycle move (also allowed to the assigned technician). */
    @Transactional
    public Job changeStatus(UUID id, String status) {
        Job job = get(id);
        Job.Status from = job.getStatus();
        job.moveTo(parseStatus(status), Instant.now(clock));
        jobs.saveAndFlush(job);
        if (from != job.getStatus()) {
            audit.action("STATUS", "JOB", id, job.getProjectId(), id,
                    Map.of("from", from.name(), "to", job.getStatus().name()));
        }
        return job;
    }

    /** Used by visits: scheduling a NEW job marks it SCHEDULED; starting a visit starts the job. */
    @Transactional
    public void advanceForVisit(UUID jobId, Job.Status target) {
        Job job = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Job"));
        boolean moves = switch (target) {
            case SCHEDULED -> job.getStatus() == Job.Status.NEW;
            case IN_PROGRESS -> job.getStatus() == Job.Status.NEW || job.getStatus() == Job.Status.SCHEDULED
                    || job.getStatus() == Job.Status.ON_HOLD;
            default -> job.getStatus().canMoveTo(target);
        };
        if (moves) {
            Job.Status from = job.getStatus();
            job.moveTo(target, Instant.now(clock));
            audit.action("STATUS", "JOB", jobId, job.getProjectId(), jobId,
                    Map.of("from", from.name(), "to", target.name()));
        }
    }

    /** Visit assignees become job assignees, so they can see the job they visit. */
    @Transactional
    public void ensureAssigned(UUID jobId, Set<UUID> userIds) {
        Job job = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Job"));
        Set<UUID> all = new HashSet<>(job.getAssigneeUserIds());
        if (all.addAll(validAssignees(userIds))) {
            job.assign(all);
        }
    }

    /** A preventive-maintenance plan generated this job (no default SLA: it is planned work). */
    @Transactional
    public Job createForPlan(JobData d, UUID planId, Set<UUID> equipmentIds) {
        Job job = createJob(d); // contractual visits do not count toward the monthly job limit
        job.originateFromPlan(planId);
        job.replaceEquipment(equipmentIds);
        return jobs.saveAndFlush(job);
    }

    /**
     * Explicit hours win; otherwise unplanned work at a site covered by an active maintenance agreement
     * gets that agreement's response time (the shortest one if several apply).
     */
    private Instant responseDeadline(Integer hours, Job job, Instant now) {
        Integer h = hours;
        if (h == null && job.getType() != Job.Type.PREVENTIVE_MAINTENANCE) {
            h = slaPolicy.responseHoursAt(job.getLocationId()).orElse(null);
        }
        return h == null ? null : now.plus(java.time.Duration.ofHours(h));
    }

    /** Tells newly assigned technicians (not the person who assigned them) about the work order. */
    private void notifyAssigned(Job job, Set<UUID> before) {
        UUID actor = TenantContext.userId().orElse(null);
        Set<UUID> added = new HashSet<>(job.getAssigneeUserIds());
        added.removeAll(before);
        if (actor != null) {
            added.remove(actor);
        }
        if (!added.isEmpty()) {
            events.publishEvent(new JobAssigned(TenantContext.requireWorkspaceId(), job.getId(), job.getTitle(), actor,
                    added));
        }
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
    public List<JobOverview> ofProjects(Collection<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return jobs.findByProjectIdInOrderByStartDateDesc(new HashSet<>(projectIds)).stream().map(JobService::overview)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobOverview> ofContact(UUID contactId) {
        return jobs.findByContactIdOrderByCreatedAtDesc(contactId).stream().map(JobService::overview).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal standaloneBudgetTotalOfContact(UUID contactId) {
        return jobs.standaloneBudgetTotalOfContact(contactId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal materialsCostOfJob(UUID jobId) {
        return materials.costOfJob(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal materialsCostOfProject(UUID projectId) {
        return materials.costOfProject(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal materialsCostOfContact(UUID contactId) {
        return materials.costOfContact(contactId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobSummary> find(UUID jobId) {
        return jobId == null ? Optional.empty()
                : jobs.findById(jobId).map(j -> new JobSummary(j.getId(), j.getProjectId(), j.getTitle(), j.getBudget(),
                        j.getContactId(), j.getLocationId()));
    }

    // ------------------------------------------------------------------ helpers

    private static JobOverview overview(Job j) {
        return new JobOverview(j.getId(), j.getProjectId(), j.getTitle(), j.getStartDate(), j.getEstimatedEndDate(),
                j.getBudget(), j.getType().name(), j.getStatus().name(), j.getPriority().name(), j.getLocationId());
    }

    /**
     * A project job takes the project's customer (and its site unless another site of that customer
     * is given); a standalone job needs a customer and one of that customer's sites.
     */
    private Site resolveSite(UUID projectId, UUID contactId, UUID locationId) {
        if (projectId != null) {
            Projects.ProjectSummary project = projects.find(projectId)
                    .orElseThrow(() -> ApiException.referenceNotFound("projectId"));
            UUID location = locationId == null ? project.locationId() : locationId;
            requireLocationOf(project.contactId(), location);
            return new Site(project.contactId(), location);
        }
        if (contactId == null) {
            throw ApiException.badRequest("JOB_CUSTOMER_REQUIRED", "A job needs a project or a customer");
        }
        contacts.find(contactId).orElseThrow(() -> ApiException.referenceNotFound("contactId"));
        if (locationId == null) {
            throw ApiException.badRequest("JOB_SITE_REQUIRED", "Choose the customer's site for this job");
        }
        requireLocationOf(contactId, locationId);
        return new Site(contactId, locationId);
    }

    private void requireLocationOf(UUID contactId, UUID locationId) {
        Contacts.LocationSummary location = contacts.location(locationId)
                .orElseThrow(() -> ApiException.referenceNotFound("locationId"));
        if (!location.contactId().equals(contactId)) {
            throw ApiException.badRequest("LOCATION_NOT_OF_CUSTOMER", "The site does not belong to this customer");
        }
    }

    private Set<UUID> validAssignees(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> unique = new LinkedHashSet<>(userIds);
        if (unique.size() > 20) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A job can have at most 20 assigned technicians");
        }
        for (UUID u : unique) {
            if (!members.isActiveMember(u)) {
                throw ApiException.referenceNotFound("assigneeUserIds");
            }
        }
        return unique;
    }

    private Team requireNoTeam(JobData d) {
        if (d.teamManagerUserId() != null || (d.teamMemberUserIds() != null && !d.teamMemberUserIds().isEmpty())) {
            throw ApiException.badRequest("TEAM_NEEDS_PROJECT",
                    "Project teams only apply to project jobs; assign technicians instead");
        }
        return new Team(null, Set.of());
    }

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
        Set<UUID> teamMembers = new LinkedHashSet<>();
        users.forEach(u -> teamMembers.add(projects.ensureTeamMember(projectId, u)));
        return new Team(manager, teamMembers);
    }

    private Team currentTeamAsUsers(Job job) {
        Map<UUID, UUID> userByMember = projects.team(job.getProjectId()).stream()
                .collect(Collectors.toMap(Projects.TeamMemberSummary::teamMemberId, Projects.TeamMemberSummary::userId));
        UUID manager = job.getTeamManagerMemberId() == null ? null : userByMember.get(job.getTeamManagerMemberId());
        Set<UUID> teamMembers = job.getMemberIds().stream().map(userByMember::get).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new Team(manager, teamMembers);
    }

    private void publishTags(UUID jobId, Set<UUID> added, Set<UUID> removed) {
        if (!added.isEmpty() || !removed.isEmpty()) {
            events.publishEvent(new TagsChanged(TenantContext.requireWorkspaceId(), TaggableType.JOB, jobId, added,
                    removed, AutomationChain.nextDepth()));
        }
    }

    static Job.Type parseType(String value) {
        if (value == null) {
            return Job.Type.CORRECTIVE_REPAIR;
        }
        try {
            return Job.Type.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "type must be INSTALLATION, PREVENTIVE_MAINTENANCE, CORRECTIVE_REPAIR or INSPECTION");
        }
    }

    static Job.Priority parsePriority(String value) {
        if (value == null) {
            return Job.Priority.NORMAL;
        }
        try {
            return Job.Priority.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "priority must be LOW, NORMAL, HIGH or URGENT");
        }
    }

    static Job.Status parseStatus(String value) {
        try {
            return Job.Status.valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "status must be NEW, SCHEDULED, IN_PROGRESS, ON_HOLD, COMPLETED or CANCELLED");
        }
    }

    private static List<UUID> idsOf(List<FileSummary> owned, FileKind kind) {
        return owned.stream().filter(f -> f.kind() == kind).map(FileSummary::id).toList();
    }

    private static List<UUID> nonNull(List<UUID> ids) {
        return ids == null ? List.of() : ids;
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
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
        m.put("type", j.getType().name());
        m.put("priority", j.getPriority().name());
        m.put("status", j.getStatus().name());
        m.put("budget", j.getBudget() == null ? null
                : j.getBudget().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        m.put("startDate", String.valueOf(j.getStartDate()));
        m.put("estimatedEndDate", String.valueOf(j.getEstimatedEndDate()));
        m.put("locationId", j.getLocationId());
        m.put("teamManagerMemberId", j.getTeamManagerMemberId());
        m.put("assigneeUserIds", j.getAssigneeUserIds().stream().map(UUID::toString).sorted().toList());
        m.put("tagIds", j.getTagIds().stream().map(UUID::toString).sorted().toList());
        return m;
    }

    private record Team(UUID manager, Set<UUID> members) {
    }

    private record Site(UUID contactId, UUID locationId) {
    }

    public record JobData(UUID projectId, String title, String description, LocalDate startDate,
            LocalDate estimatedEndDate, BigDecimal budget, UUID teamManagerUserId, List<UUID> teamMemberUserIds,
            UUID boardTemplateId, List<UUID> mediaFileIds, List<UUID> documentFileIds, Set<UUID> tagIds,
            UUID contactId, UUID locationId, String type, String priority, String reportedIssue, String internalNotes,
            Set<UUID> assigneeUserIds, Integer responseHours) {
    }

    public record JobFilter(UUID projectId, UUID tagId, LocalDate startFrom, LocalDate startTo, String q,
            UUID contactId, UUID locationId, String type, String status, String priority, UUID assigneeUserId,
            Boolean standalone, Boolean slaBreached) {

        JobFilter withAssignee(UUID userId) {
            return new JobFilter(projectId, tagId, startFrom, startTo, q, contactId, locationId, type, status, priority,
                    userId, standalone, slaBreached);
        }
    }
}

package com.crmplatform.operations.jobs.web;

import com.crmplatform.crm.Contacts;
import com.crmplatform.crm.Contacts.ContactSummary;
import com.crmplatform.crm.Contacts.LocationSummary;
import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.crm.LabelCatalog.LabelSummary;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files.FileSummary;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.identity.WorkspaceMembers.MemberSummary;
import com.crmplatform.operations.jobs.application.JobService;
import com.crmplatform.operations.jobs.application.JobService.JobData;
import com.crmplatform.operations.jobs.application.JobService.JobFilter;
import com.crmplatform.operations.jobs.domain.Job;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.shared.web.Patches;
import com.crmplatform.work.Boards;
import com.crmplatform.work.Boards.OwnerType;
import com.crmplatform.work.Projects;
import com.crmplatform.work.Projects.TeamMemberSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Jobs / work orders and their files (FR-OPS-01..05, directive §5C). Technicians
 * ({@code ops:assigned}) reach only their assigned jobs — the service enforces it. The job board is
 * served by the work module.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs")
public class JobController {

    static final String TYPE = "^(INSTALLATION|PREVENTIVE_MAINTENANCE|CORRECTIVE_REPAIR|INSPECTION)$";
    static final String PRIORITY = "^(LOW|NORMAL|HIGH|URGENT)$";
    private static final Map<String, String> SORT = Map.of("createdAt", "createdAt", "updatedAt", "updatedAt",
            "title", "title", "startDate", "startDate", "estimatedEndDate", "estimatedEndDate", "budget", "budget",
            "priority", "priority", "status", "status");

    private final JobService jobs;
    private final Projects projects;
    private final Contacts contacts;
    private final WorkspaceMembers members;
    private final Boards boards;
    private final LabelCatalog labels;
    private final Patches patches;

    public JobController(JobService jobs, Projects projects, Contacts contacts, WorkspaceMembers members, Boards boards,
            LabelCatalog labels, Patches patches) {
        this.jobs = jobs;
        this.projects = projects;
        this.contacts = contacts;
        this.members = members;
        this.boards = boards;
        this.labels = labels;
        this.patches = patches;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    public PageResponse<JobResponse> list(@RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
            @RequestParam(required = false) String q, @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID locationId, @RequestParam(required = false) String type,
            @RequestParam(required = false) String status, @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID assigneeUserId, @RequestParam(required = false) Boolean standalone,
            @RequestParam(required = false) Boolean slaBreached,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Job> result = jobs.search(new JobFilter(projectId, tagId, startFrom, startTo, q, contactId, locationId, type,
                status, priority, assigneeUserId, standalone, slaBreached), pageable);
        Assembler assembler = new Assembler(result.getContent());
        return PageResponse.of(result.map(j -> assembler.toResponse(j, null, null, null)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    public JobResponse get(@PathVariable UUID id) {
        return detail(jobs.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ops:write')")
    public ResponseEntity<JobResponse> create(@Valid @RequestBody JobRequest body) {
        Job j = jobs.create(body.toData());
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + j.getId())).body(detail(jobs.get(j.getId())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:write')")
    public JobResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        var patch = patches.read(body, JobRequest.class);
        if (patch.has("projectId")) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A job cannot be moved to another project");
        }
        jobs.update(id, new Patch<>(patch.value().toData(), patch.fields()));
        return detail(jobs.get(id));
    }

    /** Lifecycle move; the assigned technician may do this too. */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public JobResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest body) {
        return detail(jobs.changeStatus(id, body.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        jobs.delete(id);
    }

    @PostMapping("/{id}/files")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public FilesResponse addFiles(@PathVariable UUID id, @Valid @RequestBody AddFilesRequest body) {
        return FilesResponse.of(jobs.addFiles(id, body.fileIds()));
    }

    @DeleteMapping("/{id}/files/{fileId}")
    @PreAuthorize("hasAuthority('ops:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFile(@PathVariable UUID id, @PathVariable UUID fileId) {
        jobs.removeFile(id, fileId);
    }

    // ------------------------------------------------------------------ assembly

    private JobResponse detail(Job j) {
        Map<UUID, TeamMemberSummary> team = j.getProjectId() == null ? Map.of()
                : projects.team(j.getProjectId()).stream()
                        .collect(Collectors.toMap(TeamMemberSummary::teamMemberId, Function.identity()));
        UUID boardId = boards.boardOf(OwnerType.JOB, j.getId()).orElse(null);
        return new Assembler(List.of(j)).toResponse(j, team, FilesResponse.of(jobs.filesOf(j.getId())), boardId);
    }

    /** Resolves tags, customers, sites and technicians for a page of jobs in bulk (no per-row queries). */
    private final class Assembler {

        private final Map<UUID, LabelSummary> tagMap;
        private final Map<UUID, ContactSummary> customers;
        private final Map<UUID, LocationSummary> sites;
        private final Map<UUID, MemberSummary> people;

        Assembler(Collection<Job> page) {
            Set<UUID> tagIds = new HashSet<>();
            Set<UUID> contactIds = new HashSet<>();
            Set<UUID> locationIds = new HashSet<>();
            Set<UUID> userIds = new HashSet<>();
            page.forEach(j -> {
                tagIds.addAll(j.getTagIds());
                contactIds.add(j.getContactId());
                locationIds.add(j.getLocationId());
                userIds.addAll(j.getAssigneeUserIds());
            });
            this.tagMap = labels.tags(tagIds);
            this.customers = contacts.findAll(contactIds);
            this.sites = contacts.locations(locationIds);
            this.people = members.findAll(userIds);
        }

        JobResponse toResponse(Job j, Map<UUID, TeamMemberSummary> team, FilesResponse files, UUID boardId) {
            List<LabelSummary> tags = new ArrayList<>(j.getTagIds().stream().map(tagMap::get).filter(Objects::nonNull)
                    .toList());
            tags.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            Member manager = j.getTeamManagerMemberId() == null ? null : member(j.getTeamManagerMemberId(), team);
            List<Member> teamMembers = j.getMemberIds().stream().map(id -> member(id, team)).toList();
            List<Technician> assignees = j.getAssigneeUserIds().stream().map(u -> {
                MemberSummary p = people.get(u);
                return new Technician(u, p == null ? null : p.fullName());
            }).toList();
            ContactSummary c = customers.get(j.getContactId());
            LocationSummary l = sites.get(j.getLocationId());
            return new JobResponse(j.getId(), j.getProjectId(), j.getType().name(), j.getStatus().name(),
                    j.getPriority().name(), j.getTitle(), j.getDescription(), j.getReportedIssue(), j.getInternalNotes(),
                    c == null ? null : new Customer(c.id(), c.fullName(), c.phone(), c.mobile(), c.email()),
                    l == null ? null : new Site(l.id(), l.name(), l.city(), l.addressLine1(), l.accessInstructions(),
                            l.latitude(), l.longitude()),
                    assignees, j.getStartDate(), j.getEstimatedEndDate(), j.getActualStartAt(), j.getCompletedAt(),
                    money(j.getBudget()), manager, teamMembers, tags,
                    files == null ? null : files.media(), files == null ? null : files.documents(), boardId,
                    Sla.of(j, Instant.now()), j.getMaintenancePlanId(), j.getCreatedAt(), j.getUpdatedAt(), j.getVersion());
        }
    }

    private static Member member(UUID teamMemberId, Map<UUID, TeamMemberSummary> team) {
        TeamMemberSummary s = team == null ? null : team.get(teamMemberId);
        return s == null ? new Member(teamMemberId, null, null) : new Member(teamMemberId, s.userId(), s.name());
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ------------------------------------------------------------------ DTOs

    /** {@code projectId} for a project job; otherwise {@code contactId} + {@code locationId} (service job). */
    public record JobRequest(
            UUID projectId,
            @NotBlank @Size(max = 150) String title,
            @Size(max = 5000) String description,
            LocalDate startDate,
            LocalDate estimatedEndDate,
            @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal budget,
            UUID teamManagerUserId,
            @Size(max = 50) List<UUID> teamMemberUserIds,
            UUID boardTemplateId,
            @Size(max = 50) List<UUID> mediaFileIds,
            @Size(max = 20) List<UUID> documentFileIds,
            @Size(max = 20) Set<UUID> tagIds,
            UUID contactId,
            UUID locationId,
            @Pattern(regexp = TYPE) String type,
            @Pattern(regexp = PRIORITY) String priority,
            @Size(max = 2000) String reportedIssue,
            @Size(max = 5000) String internalNotes,
            @Size(max = 20) Set<UUID> assigneeUserIds,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(720) Integer responseHours) {

        JobData toData() {
            return new JobData(projectId, title, description, startDate, estimatedEndDate, budget, teamManagerUserId,
                    teamMemberUserIds, boardTemplateId, mediaFileIds, documentFileIds, tagIds, contactId, locationId,
                    type, priority, reportedIssue, internalNotes, assigneeUserIds, responseHours);
        }
    }

    public record StatusRequest(
            @NotBlank @Pattern(regexp = "^(NEW|SCHEDULED|IN_PROGRESS|ON_HOLD|COMPLETED|CANCELLED)$") String status) {
    }

    public record AddFilesRequest(@NotEmpty @Size(max = 50) List<UUID> fileIds) {
    }

    public record Member(UUID teamMemberId, UUID userId, String name) {
    }

    public record Technician(UUID userId, String name) {
    }

    public record Customer(UUID id, String name, String phone, String mobile, String email) {
    }

    public record Site(UUID id, String name, String city, String addressLine1, String accessInstructions,
            BigDecimal latitude, BigDecimal longitude) {
    }

    public record FileRef(UUID id, String name, String contentType, long sizeBytes) {
        static FileRef of(FileSummary f) {
            return new FileRef(f.id(), f.name(), f.contentType(), f.sizeBytes());
        }
    }

    public record FilesResponse(List<FileRef> media, List<FileRef> documents) {
        static FilesResponse of(List<FileSummary> files) {
            return new FilesResponse(
                    files.stream().filter(f -> f.kind() == FileKind.IMAGE).map(FileRef::of).toList(),
                    files.stream().filter(f -> f.kind() == FileKind.DOCUMENT).map(FileRef::of).toList());
        }
    }

    public record JobResponse(UUID id, UUID projectId, String type, String status, String priority, String title,
            String description, String reportedIssue, String internalNotes, Customer customer, Site site,
            List<Technician> assignees, LocalDate startDate, LocalDate estimatedEndDate, Instant actualStartAt,
            Instant completedAt, String budget, Member teamManager, List<Member> teamMembers, List<LabelSummary> tags,
            List<FileRef> media, List<FileRef> documents, UUID boardId, Sla sla, UUID maintenancePlanId,
            Instant createdAt, Instant updatedAt, long version) {
    }

    /** Response-time SLA: due, when work actually started, and whether the promise was broken. */
    public record Sla(Instant responseDueAt, Instant respondedAt, boolean breached) {
        static Sla of(Job j, Instant now) {
            return j.getResponseDueAt() == null ? null
                    : new Sla(j.getResponseDueAt(), j.getActualStartAt(), j.isResponseBreached(now));
        }
    }
}

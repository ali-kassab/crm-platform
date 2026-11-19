package com.crmplatform.operations.jobs.web;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.crm.LabelCatalog.LabelSummary;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files.FileSummary;
import com.crmplatform.operations.jobs.application.JobService;
import com.crmplatform.operations.jobs.application.JobService.JobData;
import com.crmplatform.operations.jobs.application.JobService.JobFilter;
import com.crmplatform.operations.jobs.domain.Job;
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
import jakarta.validation.constraints.NotNull;
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

/** Jobs and their files (FR-OPS-01..05). The job board is served by the work module. */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs")
public class JobController {

    private static final Map<String, String> SORT = Map.of("createdAt", "createdAt", "updatedAt", "updatedAt",
            "title", "title", "startDate", "startDate", "estimatedEndDate", "estimatedEndDate", "budget", "budget");

    private final JobService jobs;
    private final Projects projects;
    private final Boards boards;
    private final LabelCatalog labels;
    private final Patches patches;

    public JobController(JobService jobs, Projects projects, Boards boards, LabelCatalog labels, Patches patches) {
        this.jobs = jobs;
        this.projects = projects;
        this.boards = boards;
        this.labels = labels;
        this.patches = patches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ops:read')")
    public PageResponse<JobResponse> list(@RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Job> result = jobs.search(new JobFilter(projectId, tagId, startFrom, startTo, q), pageable);
        Map<UUID, LabelSummary> tags = tagsOf(result.getContent());
        return PageResponse.of(result.map(j -> toResponse(j, tags, null, null, null)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:read')")
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
            throw com.crmplatform.shared.error.ApiException.badRequest("VALIDATION_ERROR",
                    "A job cannot be moved to another project");
        }
        jobs.update(id, new Patch<>(patch.value().toData(), patch.fields()));
        return detail(jobs.get(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        jobs.delete(id);
    }

    @PostMapping("/{id}/files")
    @PreAuthorize("hasAuthority('ops:write')")
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
        Map<UUID, TeamMemberSummary> team = projects.team(j.getProjectId()).stream()
                .collect(Collectors.toMap(TeamMemberSummary::teamMemberId, Function.identity()));
        UUID boardId = boards.boardOf(OwnerType.JOB, j.getId()).orElse(null);
        return toResponse(j, tagsOf(List.of(j)), team, FilesResponse.of(jobs.filesOf(j.getId())), boardId);
    }

    private JobResponse toResponse(Job j, Map<UUID, LabelSummary> tagMap, Map<UUID, TeamMemberSummary> team,
            FilesResponse files, UUID boardId) {
        List<LabelSummary> tags = new ArrayList<>(j.getTagIds().stream().map(tagMap::get).filter(Objects::nonNull)
                .toList());
        tags.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        Member manager = j.getTeamManagerMemberId() == null ? null : member(j.getTeamManagerMemberId(), team);
        List<Member> members = j.getMemberIds().stream().map(id -> member(id, team)).toList();
        return new JobResponse(j.getId(), j.getProjectId(), j.getTitle(), j.getDescription(), j.getStartDate(),
                j.getEstimatedEndDate(), money(j.getBudget()), manager, members, tags,
                files == null ? null : files.media(), files == null ? null : files.documents(), boardId,
                j.getCreatedAt(), j.getUpdatedAt(), j.getVersion());
    }

    private static Member member(UUID teamMemberId, Map<UUID, TeamMemberSummary> team) {
        TeamMemberSummary s = team == null ? null : team.get(teamMemberId);
        return s == null ? new Member(teamMemberId, null, null) : new Member(teamMemberId, s.userId(), s.name());
    }

    private Map<UUID, LabelSummary> tagsOf(Collection<Job> list) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(j -> ids.addAll(j.getTagIds()));
        return labels.tags(ids);
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ------------------------------------------------------------------ DTOs

    public record JobRequest(
            @NotNull UUID projectId,
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull LocalDate startDate,
            @NotNull LocalDate estimatedEndDate,
            @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal budget,
            UUID teamManagerUserId,
            @Size(max = 50) List<UUID> teamMemberUserIds,
            UUID boardTemplateId,
            @Size(max = 50) List<UUID> mediaFileIds,
            @Size(max = 20) List<UUID> documentFileIds,
            @Size(max = 20) Set<UUID> tagIds) {

        JobData toData() {
            return new JobData(projectId, title, description, startDate, estimatedEndDate, budget, teamManagerUserId,
                    teamMemberUserIds, boardTemplateId, mediaFileIds, documentFileIds, tagIds);
        }
    }

    public record AddFilesRequest(@NotEmpty @Size(max = 50) List<UUID> fileIds) {
    }

    public record Member(UUID teamMemberId, UUID userId, String name) {
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

    public record JobResponse(UUID id, UUID projectId, String title, String description, LocalDate startDate,
            LocalDate estimatedEndDate, String budget, Member teamManager, List<Member> teamMembers,
            List<LabelSummary> tags, List<FileRef> media, List<FileRef> documents, UUID boardId, Instant createdAt,
            Instant updatedAt, long version) {
    }
}

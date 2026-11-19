package com.crmplatform.work.projects.web;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.crm.LabelCatalog.LabelSummary;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.identity.WorkspaceMembers.MemberSummary;
import com.crmplatform.shared.persistence.Address;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.shared.web.Patches;
import com.crmplatform.work.Boards.OwnerType;
import com.crmplatform.work.boards.application.BoardService;
import com.crmplatform.work.boards.application.BoardViews.BoardView;
import com.crmplatform.work.projects.application.ProjectService;
import com.crmplatform.work.projects.application.ProjectService.AddressData;
import com.crmplatform.work.projects.application.ProjectService.ProjectData;
import com.crmplatform.work.projects.application.ProjectService.ProjectFilter;
import com.crmplatform.work.projects.application.TeamService;
import com.crmplatform.work.projects.domain.Project;
import com.crmplatform.work.projects.domain.TeamMember;
import com.crmplatform.work.projects.domain.TeamMember.Responsibility;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

/** Projects, their board and their team (FR-WRK-01..06, FR-WRK-09). */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects")
public class ProjectController {

    private static final Map<String, String> SORT = Map.of("createdAt", "createdAt", "updatedAt", "updatedAt",
            "name", "name", "startDate", "startDate", "estimatedEndDate", "estimatedEndDate", "budget", "budget");

    private final ProjectService projects;
    private final TeamService team;
    private final BoardService boards;
    private final LabelCatalog labels;
    private final WorkspaceMembers members;
    private final Patches patches;

    public ProjectController(ProjectService projects, TeamService team, BoardService boards, LabelCatalog labels,
            WorkspaceMembers members, Patches patches) {
        this.projects = projects;
        this.team = team;
        this.boards = boards;
        this.labels = labels;
        this.members = members;
        this.patches = patches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('work:read')")
    public PageResponse<ProjectResponse> list(@RequestParam(required = false) String type,
            @RequestParam(required = false) UUID tagId, @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
            @RequestParam(required = false) Boolean overBudget, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Project> result = projects.search(new ProjectFilter(type, tagId, locationId, contactId, startFrom, startTo,
                overBudget, q), pageable);
        Assembler assembler = new Assembler(result.getContent());
        return PageResponse.of(result.map(p -> assembler.toResponse(p, null, null)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('work:read')")
    public ProjectResponse get(@PathVariable UUID id) {
        return detail(projects.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('work:write')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest body) {
        Project p = projects.create(body.toData());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + p.getId())).body(detail(projects.get(p.getId())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('work:write')")
    public ProjectResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        var patch = patches.read(body, ProjectRequest.class);
        projects.update(id, new Patch<>(patch.value().toData(), patch.fields()));
        return detail(projects.get(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('work:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        projects.delete(id);
    }

    @GetMapping("/{id}/board")
    @PreAuthorize("hasAuthority('work:read')")
    public BoardView board(@PathVariable UUID id) {
        projects.get(id);
        return boards.viewOf(OwnerType.PROJECT, id);
    }

    @GetMapping("/{id}/team-members")
    @PreAuthorize("hasAuthority('work:read')")
    public List<TeamMemberResponse> teamMembers(@PathVariable UUID id) {
        projects.get(id);
        List<TeamMember> list = projects.teamOf(id);
        Map<UUID, MemberSummary> people = members.findAll(list.stream().map(TeamMember::getUserId).toList());
        return list.stream()
                .sorted((a, b) -> Boolean.compare(b.isManager(), a.isManager()))
                .map(tm -> TeamMemberResponse.of(tm, people.get(tm.getUserId()))).toList();
    }

    @PostMapping("/{id}/team-members")
    @PreAuthorize("hasAuthority('work:write')")
    public ResponseEntity<TeamMemberResponse> addTeamMember(@PathVariable UUID id,
            @Valid @RequestBody AddTeamMemberRequest body) {
        TeamMember tm = team.add(id, body.userId(), body.position(), body.responsibilities());
        return ResponseEntity.created(URI.create("/api/v1/team-members/" + tm.getId()))
                .body(TeamMemberResponse.of(tm, members.findAll(List.of(tm.getUserId())).get(tm.getUserId())));
    }

    private ProjectResponse detail(Project p) {
        List<TeamMemberResponse> teamList = teamMembers(p.getId());
        UUID boardId = boards.boardOf(OwnerType.PROJECT, p.getId()).orElse(null);
        return new Assembler(List.of(p)).toResponse(p, teamList, boardId);
    }

    /** Resolves tags, remaining budgets and managers for a page of projects in bulk. */
    private final class Assembler {

        private final Map<UUID, LabelSummary> tagSummaries;
        private final Map<UUID, BigDecimal> remaining;

        Assembler(Collection<Project> page) {
            Set<UUID> tagIds = new HashSet<>();
            page.forEach(p -> tagIds.addAll(p.getTagIds()));
            this.tagSummaries = labels.tags(tagIds);
            this.remaining = projects.remainingBudgets(page);
        }

        ProjectResponse toResponse(Project p, List<TeamMemberResponse> teamList, UUID boardId) {
            List<LabelSummary> tags = new ArrayList<>(p.getTagIds().stream().map(tagSummaries::get)
                    .filter(Objects::nonNull).toList());
            tags.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            Address a = p.getAddress();
            return new ProjectResponse(p.getId(), p.getName(), p.getDescription(), p.getType().name(),
                    a == null ? null : new AddressData(a.getLine1(), a.getLine2(), a.getCity(), a.getRegion(),
                            a.getPostalCode(), a.getCountry()),
                    decimal(p.getTotalArea()), money(p.getBudget()), money(remaining.get(p.getId())),
                    p.getStartDate(), p.getEstimatedEndDate(), p.getLocationId(), p.getContactId(), tags, teamList,
                    boardId, p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
        }
    }

    static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ DTOs

    public record AddressRequest(@Size(max = 200) String line1, @Size(max = 200) String line2,
            @NotBlank @Size(max = 100) String city, @NotBlank @Size(max = 100) String region,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166 alpha-2 code") String country) {
        AddressData toData() {
            return new AddressData(line1, line2, city, region, postalCode, country == null ? null : country.toUpperCase());
        }
    }

    public record ProjectRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 5000) String description,
            @Pattern(regexp = "^(COMMERCIAL|RESIDENTIAL|INDUSTRIAL|INSTITUTIONAL|INFRASTRUCTURE)$") String type,
            @Valid @NotNull AddressRequest address,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal totalArea,
            @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal budget,
            @NotNull LocalDate startDate,
            @NotNull LocalDate estimatedEndDate,
            @NotNull UUID locationId,
            @NotNull UUID projectManagerUserId,
            @Size(max = 50) List<UUID> teamMemberUserIds,
            UUID boardTemplateId,
            @Size(max = 20) List<UUID> documentFileIds,
            @Size(max = 20) Set<UUID> tagIds) {

        ProjectData toData() {
            return new ProjectData(name, description, type, address == null ? null : address.toData(), totalArea,
                    budget, startDate, estimatedEndDate, locationId, projectManagerUserId, teamMemberUserIds,
                    boardTemplateId, documentFileIds, tagIds);
        }
    }

    public record AddTeamMemberRequest(@NotNull UUID userId, @Size(max = 100) String position,
            Set<Responsibility> responsibilities) {
    }

    public record ProjectResponse(UUID id, String name, String description, String type, AddressData address,
            String totalArea, String budget, String remainingBudget, LocalDate startDate, LocalDate estimatedEndDate,
            UUID locationId, UUID contactId, List<LabelSummary> tags, List<TeamMemberResponse> team, UUID boardId,
            Instant createdAt, Instant updatedAt, long version) {
    }

    public record TeamMemberResponse(UUID id, UUID projectId, UUID userId, String name, String email,
            String position, boolean manager, List<String> responsibilities, Instant createdAt) {

        public static TeamMemberResponse of(TeamMember tm, MemberSummary person) {
            return new TeamMemberResponse(tm.getId(), tm.getProjectId(), tm.getUserId(),
                    person == null ? "Former member" : person.fullName(), person == null ? null : person.email(),
                    tm.getPosition(), tm.isManager(),
                    tm.getResponsibilities().stream().map(Enum::name).sorted().toList(), tm.getCreatedAt());
        }
    }
}

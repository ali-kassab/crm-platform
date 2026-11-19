package com.crmplatform.operations.visits.web;

import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.identity.WorkspaceMembers.MemberSummary;
import com.crmplatform.operations.visits.application.VisitService;
import com.crmplatform.operations.visits.application.VisitService.VisitFilter;
import com.crmplatform.operations.visits.domain.JobVisit;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visit scheduling and execution (directive §5D). Dispatchers ({@code ops:write}) and assigned
 * technicians ({@code ops:assigned}) use the same endpoints; technicians only ever reach their
 * own visits.
 */
@RestController
@Tag(name = "Visits")
public class VisitController {

    private static final Map<String, String> SORT = Map.of("scheduledStart", "scheduledStart",
            "createdAt", "createdAt");

    private final VisitService visits;
    private final WorkspaceMembers members;

    public VisitController(VisitService visits, WorkspaceMembers members) {
        this.visits = visits;
        this.members = members;
    }

    @GetMapping("/api/v1/jobs/{jobId}/visits")
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    public List<VisitResponse> ofJob(@PathVariable UUID jobId) {
        List<JobVisit> list = visits.ofJob(jobId);
        Map<UUID, MemberSummary> people = people(list);
        return list.stream().map(v -> VisitResponse.of(v, people)).toList();
    }

    @PostMapping("/api/v1/jobs/{jobId}/visits")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    @Operation(summary = "Schedule a visit; without assignees it goes to the job's technicians (or the caller)")
    public ResponseEntity<VisitResponse> schedule(@PathVariable UUID jobId, @Valid @RequestBody ScheduleRequest body) {
        JobVisit v = visits.schedule(jobId, body.scheduledStart(), body.scheduledEnd(), body.assigneeUserIds(),
                body.notes());
        return ResponseEntity.created(URI.create("/api/v1/visits/" + v.getId())).body(VisitResponse.of(v, people(List.of(v))));
    }

    /** Calendar / "my visits": {@code assignee=me}, date range, status, {@code overdue=true}. */
    @GetMapping("/api/v1/visits")
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    public PageResponse<VisitResponse> list(@RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) String assignee, @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Boolean overdue, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.ASC, "scheduledStart"));
        UUID assigneeId = assignee == null ? null : "me".equals(assignee) ? CurrentUser.id() : UUID.fromString(assignee);
        Page<JobVisit> result = visits.search(new VisitFilter(jobId, assigneeId, status, from, to, overdue), pageable);
        Map<UUID, MemberSummary> people = people(result.getContent());
        return PageResponse.of(result.map(v -> VisitResponse.of(v, people)));
    }

    @GetMapping("/api/v1/visits/{id}")
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    public VisitResponse get(@PathVariable UUID id) {
        JobVisit v = visits.get(id);
        return VisitResponse.of(v, people(List.of(v)));
    }

    @PostMapping("/api/v1/visits/{id}/start")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public VisitResponse start(@PathVariable UUID id) {
        JobVisit v = visits.start(id);
        return VisitResponse.of(v, people(List.of(v)));
    }

    @PostMapping("/api/v1/visits/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public VisitResponse complete(@PathVariable UUID id, @Valid @RequestBody CompleteRequest body) {
        JobVisit v = visits.complete(id, body.outcome(), body.notes(), Boolean.TRUE.equals(body.completeJob()));
        return VisitResponse.of(v, people(List.of(v)));
    }

    @PostMapping("/api/v1/visits/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public VisitResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest body) {
        JobVisit v = visits.cancel(id, body.reason());
        return VisitResponse.of(v, people(List.of(v)));
    }

    @PostMapping("/api/v1/visits/{id}/reschedule")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public ResponseEntity<VisitResponse> reschedule(@PathVariable UUID id, @Valid @RequestBody ScheduleRequest body) {
        JobVisit v = visits.reschedule(id, body.scheduledStart(), body.scheduledEnd(), body.assigneeUserIds());
        return ResponseEntity.created(URI.create("/api/v1/visits/" + v.getId())).body(VisitResponse.of(v, people(List.of(v))));
    }

    private Map<UUID, MemberSummary> people(Collection<JobVisit> list) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(v -> ids.addAll(v.getAssigneeUserIds()));
        return members.findAll(ids);
    }

    // ------------------------------------------------------------------ DTOs

    public record ScheduleRequest(@NotNull Instant scheduledStart, @NotNull Instant scheduledEnd,
            @Size(max = 10) Set<UUID> assigneeUserIds, @Size(max = 2000) String notes) {
    }

    public record CompleteRequest(@NotBlank String outcome, @Size(max = 2000) String notes, Boolean completeJob) {
    }

    public record CancelRequest(@Size(max = 2000) String reason) {
    }

    public record Assignee(UUID userId, String name) {
    }

    public record VisitResponse(UUID id, UUID jobId, Instant scheduledStart, Instant scheduledEnd, String status,
            Instant actualStartAt, Instant actualEndAt, String outcome, String notes, UUID rescheduledFromId,
            List<Assignee> assignees, Instant createdAt, Instant updatedAt, long version) {

        static VisitResponse of(JobVisit v, Map<UUID, MemberSummary> people) {
            return new VisitResponse(v.getId(), v.getJobId(), v.getScheduledStart(), v.getScheduledEnd(),
                    v.getStatus().name(), v.getActualStartAt(), v.getActualEndAt(),
                    v.getOutcome() == null ? null : v.getOutcome().name(), v.getNotes(), v.getRescheduledFromId(),
                    v.getAssigneeUserIds().stream().map(u -> {
                        MemberSummary p = people.get(u);
                        return new Assignee(u, p == null ? null : p.fullName());
                    }).toList(),
                    v.getCreatedAt(), v.getUpdatedAt(), v.getVersion());
        }
    }
}

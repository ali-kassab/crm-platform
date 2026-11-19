package com.crmplatform.work.boards.web;

import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.identity.WorkspaceMembers.MemberSummary;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.shared.web.Patches;
import com.crmplatform.work.boards.application.BoardService;
import com.crmplatform.work.boards.application.BoardViews.BoardView;
import com.crmplatform.work.boards.application.BoardViews.TaskView;
import com.crmplatform.work.boards.application.TaskService;
import com.crmplatform.work.boards.application.TaskService.TaskData;
import com.crmplatform.work.boards.application.TaskService.TaskFilter;
import com.crmplatform.work.boards.domain.TaskComment;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

/** Tasks, moves and comments (FR-WRK-12..17). */
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks")
public class TaskController {

    private static final Map<String, String> SORT = Map.of("createdAt", "createdAt", "updatedAt", "updatedAt",
            "title", "title", "startDate", "startDate", "endDate", "endDate", "priority", "priority",
            "status", "status", "position", "position");

    private final TaskService tasks;
    private final BoardService boards;
    private final WorkspaceMembers members;
    private final Patches patches;

    public TaskController(TaskService tasks, BoardService boards, WorkspaceMembers members, Patches patches) {
        this.tasks = tasks;
        this.boards = boards;
        this.members = members;
        this.patches = patches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('work:read')")
    public PageResponse<TaskView> list(@RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) Boolean overdue, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        if ((status != null && !status.matches(STATUS)) || (priority != null && !priority.matches(PRIORITY))) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown task status or priority");
        }
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.ASC, "endDate"));
        return PageResponse.of(tasks.search(new TaskFilter(projectId, jobId, status, priority, assigneeId, dueFrom,
                dueTo, overdue, q), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('work:read')")
    public TaskView get(@PathVariable UUID id) {
        return tasks.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('work:write')")
    public ResponseEntity<TaskView> create(@Valid @RequestBody TaskRequest body) {
        TaskView view = tasks.create(body.toData());
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + view.id())).body(tasks.get(view.id()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('work:write')")
    public TaskView update(@PathVariable UUID id, @RequestBody JsonNode body) {
        var patch = patches.read(body, TaskRequest.class);
        tasks.update(id, new Patch<>(patch.value().toData(), patch.fields()));
        return tasks.get(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('work:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        tasks.delete(id);
    }

    /** Moves the task and returns the board re-read after commit (BR-076, BR-077). */
    @PostMapping("/{id}/move")
    @PreAuthorize("hasAuthority('work:write')")
    public BoardView move(@PathVariable UUID id, @Valid @RequestBody MoveRequest body) {
        tasks.move(id, body.columnId(), body.position(), body.boardVersion());
        return boards.view(tasks.get(id).boardId());
    }

    // ------------------------------------------------------------------ comments

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('work:read')")
    public List<CommentResponse> comments(@PathVariable UUID id) {
        List<TaskComment> list = tasks.comments(id);
        Map<UUID, MemberSummary> authors = members.findAll(list.stream().map(TaskComment::getCreatedBy)
                .filter(Objects::nonNull).toList());
        return list.stream().map(c -> CommentResponse.of(c, authors.get(c.getCreatedBy()))).toList();
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('work:write')")
    public ResponseEntity<CommentResponse> addComment(@PathVariable UUID id, @Valid @RequestBody CommentRequest body) {
        TaskComment c = tasks.addComment(id, body.text(), body.imageFileId());
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + id + "/comments/" + c.getId()))
                .body(CommentResponse.of(c, author(c)));
    }

    @PatchMapping("/{id}/comments/{commentId}")
    @PreAuthorize("hasAuthority('work:write')")
    public CommentResponse editComment(@PathVariable UUID id, @PathVariable UUID commentId,
            @Valid @RequestBody CommentEditRequest body) {
        TaskComment c = tasks.editComment(id, commentId, body.text());
        return CommentResponse.of(c, author(c));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @PreAuthorize("hasAuthority('work:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID id, @PathVariable UUID commentId) {
        tasks.deleteComment(id, commentId);
    }

    private MemberSummary author(TaskComment c) {
        return c.getCreatedBy() == null ? null : members.findAll(List.of(c.getCreatedBy())).get(c.getCreatedBy());
    }

    // ------------------------------------------------------------------ DTOs

    static final String STATUS = "^(PENDING|IN_PROGRESS|ON_HOLD|COMPLETED|CANCELLED)$";
    static final String PRIORITY = "^(LOW|MEDIUM|HIGH)$";

    public record TaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Pattern(regexp = PRIORITY) String priority,
            @Pattern(regexp = STATUS) String status,
            UUID projectId,
            UUID jobId,
            UUID columnId,
            @Size(max = 20) Set<UUID> tagIds,
            @Size(max = 50) Set<UUID> assigneeTeamMemberIds) {

        TaskData toData() {
            return new TaskData(projectId, jobId, columnId, title, description == null ? "" : description, startDate,
                    endDate, priority, status, tagIds, assigneeTeamMemberIds);
        }
    }

    public record MoveRequest(@NotNull UUID columnId, @NotNull @Min(1) Integer position, @NotNull Long boardVersion) {
    }

    public record CommentRequest(@NotBlank @Size(max = 2000) String text, UUID imageFileId) {
    }

    public record CommentEditRequest(@NotBlank @Size(max = 2000) String text) {
    }

    public record CommentResponse(UUID id, UUID taskId, String text, UUID imageFileId, UUID authorUserId,
            String authorName, Instant createdAt, Instant updatedAt) {

        static CommentResponse of(TaskComment c, MemberSummary author) {
            return new CommentResponse(c.getId(), c.getTaskId(), c.getText(), c.getImageFileId(), c.getCreatedBy(),
                    author == null ? null : author.fullName(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}

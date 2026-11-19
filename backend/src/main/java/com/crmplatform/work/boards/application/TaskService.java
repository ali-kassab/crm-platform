package com.crmplatform.work.boards.application;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.work.Boards.NewTask;
import com.crmplatform.work.Boards.OwnerType;
import com.crmplatform.work.WorkEvents.TaskAssigned;
import com.crmplatform.work.WorkReferences.JobDirectory;
import com.crmplatform.work.boards.application.BoardViews.TaskView;
import com.crmplatform.work.boards.domain.Board;
import com.crmplatform.work.boards.domain.BoardColumn;
import com.crmplatform.work.boards.domain.BoardRepositories.BoardRepository;
import com.crmplatform.work.boards.domain.BoardRepositories.TaskCommentRepository;
import com.crmplatform.work.boards.domain.BoardRepositories.TaskRepository;
import com.crmplatform.work.boards.domain.Task;
import com.crmplatform.work.boards.domain.TaskComment;
import com.crmplatform.work.projects.domain.ProjectRepositories.ProjectRepository;
import com.crmplatform.work.projects.domain.ProjectRepositories.TeamMemberRepository;
import com.crmplatform.work.projects.domain.TeamMember;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tasks, moves and comments (FR-WRK-12..17, BR-073..079). */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final BoardRepository boards;
    private final BoardService boardService;
    private final ProjectRepository projects;
    private final TeamMemberRepository teamMembers;
    private final ObjectProvider<JobDirectory> jobs;
    private final LabelCatalog labels;
    private final Files files;
    private final ApplicationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;

    public TaskService(TaskRepository tasks, TaskCommentRepository comments, BoardRepository boards,
            BoardService boardService, ProjectRepository projects, TeamMemberRepository teamMembers,
            ObjectProvider<JobDirectory> jobs, LabelCatalog labels, Files files, ApplicationEventPublisher events,
            AuditTrail audit, Clock clock) {
        this.tasks = tasks;
        this.comments = comments;
        this.boards = boards;
        this.boardService = boardService;
        this.projects = projects;
        this.teamMembers = teamMembers;
        this.jobs = jobs;
        this.labels = labels;
        this.files = files;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Page<TaskView> search(TaskFilter filter, Pageable pageable) {
        Page<Task> page = tasks.findAll(TaskSpecs.matching(filter, LocalDate.now(clock)), pageable);
        BoardService.CardContext ctx = boardService.cardContext(page.getContent());
        return page.map(t -> view(t, ctx));
    }

    @Transactional(readOnly = true)
    public TaskView get(UUID id) {
        Task t = require(id);
        return view(t, boardService.cardContext(List.of(t)));
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public TaskView create(TaskData d) {
        Parent parent = resolveParent(d.projectId(), d.jobId());
        Board board = boardService.lock(boards.findByOwnerTypeAndOwnerId(parent.ownerType(), parent.ownerId())
                .orElseThrow(() -> ApiException.notFound("Board")).getId());
        if (board.getColumns().isEmpty()) {
            board.addColumn("Backlog", true); // BR-075
        }
        BoardColumn column = d.columnId() == null ? board.defaultColumn() : board.column(d.columnId());
        validateDates(d.startDate(), d.endDate());
        Task task = new Task(d.projectId(), d.jobId(), d.title(), d.description(), d.startDate(), d.endDate());
        task.place(board.getId(), column.getId(), (int) tasks.countByColumnId(column.getId()) + 1);
        task.setPriority(d.priority() == null ? Task.Priority.MEDIUM : Task.Priority.valueOf(d.priority()));
        task.setStatus(d.status() == null ? Task.Status.PENDING : Task.Status.valueOf(d.status()), Instant.now(clock));
        Set<UUID> tagIds = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
        labels.requireTags(tagIds, "tagIds");
        task.replaceTags(tagIds);
        Set<UUID> assignees = validAssignees(parent.projectId(), d.assigneeTeamMemberIds());
        task.replaceAssignees(assignees);
        Task saved = tasks.save(task);

        long version = boardService.commit(board);
        TaskView view = get(saved.getId());
        boardService.publish(board, version, "TASK_CREATED", Map.of("task", view));
        notifyAssigned(saved, Set.of(), assignees);
        audit.created("TASK", saved.getId(), parent.projectId(), d.jobId(), Map.of("title", saved.getTitle()));
        return view;
    }

    /** Used by automation CREATE_TASK. */
    @Transactional
    public UUID createFromAutomation(NewTask n) {
        Board board = boards.findByOwnerTypeAndOwnerId(n.ownerType(), n.ownerId())
                .orElseThrow(() -> ApiException.notFound("Board"));
        UUID columnId = n.columnTitle() == null ? null : board.getColumns().stream()
                .filter(c -> c.getTitle().equalsIgnoreCase(n.columnTitle())).map(BoardColumn::getId).findFirst()
                .orElse(null);
        TaskData d = new TaskData(n.ownerType() == OwnerType.PROJECT ? n.ownerId() : null,
                n.ownerType() == OwnerType.JOB ? n.ownerId() : null, columnId, n.title(),
                n.description() == null ? "" : n.description(), n.startDate(), n.endDate(), n.priority(), null,
                n.tagIds(), n.assigneeTeamMemberIds() == null ? null : new HashSet<>(n.assigneeTeamMemberIds()));
        return create(d).id();
    }

    @Transactional
    public TaskView update(UUID id, Patch<TaskData> patch) {
        Task t = require(id);
        TaskData d = patch.value();
        Map<String, Object> before = snapshot(t);
        Set<UUID> previousAssignees = t.getAssigneeIds();

        patch.ifPresent("title", d.title(), v -> t.setTitle(required(v, "title")));
        patch.ifPresent("description", d.description(), v -> t.setDescription(v == null ? "" : v));
        if (patch.has("startDate") || patch.has("endDate")) {
            LocalDate start = patch.has("startDate") ? required(d.startDate(), "startDate") : t.getStartDate();
            LocalDate end = patch.has("endDate") ? required(d.endDate(), "endDate") : t.getEndDate();
            validateDates(start, end);
            t.setSchedule(start, end);
        }
        patch.ifPresent("priority", d.priority(), v -> t.setPriority(Task.Priority.valueOf(required(v, "priority"))));
        patch.ifPresent("status", d.status(),
                v -> t.setStatus(Task.Status.valueOf(required(v, "status")), Instant.now(clock)));
        if (patch.has("tagIds")) {
            Set<UUID> tagIds = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
            labels.requireTags(tagIds, "tagIds");
            t.replaceTags(tagIds);
        }
        UUID oldBoard = t.getBoardId();
        if (patch.has("projectId") || patch.has("jobId")) {
            UUID projectId = patch.has("projectId") ? d.projectId() : t.getProjectId();
            UUID jobId = patch.has("jobId") ? d.jobId() : t.getJobId();
            if (patch.has("projectId") && d.projectId() != null && !patch.has("jobId")) {
                jobId = null;
            }
            if (patch.has("jobId") && d.jobId() != null && !patch.has("projectId")) {
                projectId = null;
            }
            rehome(t, resolveParent(projectId, jobId)); // EC-20
        }
        if (patch.has("assigneeTeamMemberIds")) {
            UUID projectForTeam = t.getProjectId() != null ? t.getProjectId()
                    : jobs.getObject().job(t.getJobId()).map(JobDirectory.JobRef::projectId).orElse(null);
            t.replaceAssignees(validAssignees(projectForTeam, d.assigneeTeamMemberIds()));
        }
        tasks.saveAndFlush(t);

        Board board = boardService.lock(t.getBoardId());
        long version = boardService.commit(board);
        TaskView view = get(id);
        if (!oldBoard.equals(t.getBoardId())) {
            Board previous = boardService.lock(oldBoard);
            boardService.publish(previous, boardService.commit(previous), "TASK_DELETED",
                    Map.of("taskId", id));
            boardService.publish(board, version, "TASK_CREATED", Map.of("task", view));
        } else {
            boardService.publish(board, version, "TASK_UPDATED", Map.of("task", view));
        }
        notifyAssigned(t, previousAssignees, t.getAssigneeIds());
        audit.updated("TASK", id, t.getProjectId(), t.getJobId(), before, snapshot(t));
        return view;
    }

    /**
     * Moves a task to {@code position} in {@code columnId}, keeping positions contiguous in both
     * columns (BR-076). A stale {@code boardVersion} yields 409 with the current board (BR-077).
     */
    @Transactional(noRollbackFor = BoardConflictException.class)
    public void move(UUID taskId, UUID columnId, int position, long boardVersion) {
        Task t = require(taskId);
        Board board = boardService.lock(t.getBoardId());
        if (board.getVersion() != boardVersion) {
            throw new BoardConflictException(boardService.render(board));
        }
        board.column(columnId);
        UUID from = t.getColumnId();
        int oldPos = t.getPosition();
        if (from.equals(columnId)) {
            int count = (int) tasks.countByColumnId(columnId);
            int target = clamp(position, 1, count);
            if (target > oldPos) {
                tasks.shift(columnId, oldPos + 1, target, -1, taskId);
            } else if (target < oldPos) {
                tasks.shift(columnId, target, oldPos - 1, 1, taskId);
            }
            t.place(board.getId(), columnId, target);
        } else {
            tasks.shift(from, oldPos + 1, Integer.MAX_VALUE, -1, taskId);
            int count = (int) tasks.countByColumnId(columnId);
            int target = clamp(position, 1, count + 1);
            tasks.shift(columnId, target, Integer.MAX_VALUE, 1, taskId);
            t.place(board.getId(), columnId, target);
        }
        tasks.saveAndFlush(t);
        long version = boardService.commit(board);
        boardService.publish(board, version, "TASK_MOVED", Map.of("taskId", taskId, "fromColumnId", from,
                "toColumnId", columnId, "position", t.getPosition()));
        audit.action("MOVE", "TASK", taskId, t.getProjectId(), t.getJobId(),
                Map.of("fromColumnId", from, "toColumnId", columnId, "position", t.getPosition()));
    }

    @Transactional
    public void delete(UUID id) {
        Task t = require(id);
        Board board = boardService.lock(t.getBoardId());
        t.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        tasks.saveAndFlush(t);
        tasks.shift(t.getColumnId(), t.getPosition() + 1, Integer.MAX_VALUE, -1, id);
        long version = boardService.commit(board);
        boardService.publish(board, version, "TASK_DELETED", Map.of("taskId", id, "columnId", t.getColumnId()));
        audit.deleted("TASK", id, t.getProjectId(), t.getJobId());
    }

    // ------------------------------------------------------------------ comments

    @Transactional(readOnly = true)
    public List<TaskComment> comments(UUID taskId) {
        require(taskId);
        return comments.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    @Transactional
    public TaskComment addComment(UUID taskId, String text, UUID imageFileId) {
        Task t = require(taskId);
        TaskComment c = comments.save(new TaskComment(taskId, text, imageFileId));
        files.attach(imageFileId == null ? List.of() : List.of(imageFileId), FileKind.IMAGE, "TASK_COMMENT",
                c.getId(), "imageFileId");
        Board board = boardService.lock(t.getBoardId());
        boardService.publish(board, boardService.commit(board), "TASK_COMMENT_ADDED",
                Map.of("taskId", taskId, "commentCount", comments.countByTaskId(taskId)));
        return c;
    }

    @Transactional
    public TaskComment editComment(UUID taskId, UUID commentId, String text) {
        TaskComment c = comment(taskId, commentId);
        if (!Objects.equals(c.getCreatedBy(), CurrentUser.id())) {
            throw ApiException.forbidden("Only the author can edit this comment");
        }
        c.edit(text);
        return c;
    }

    @Transactional
    public void deleteComment(UUID taskId, UUID commentId) {
        TaskComment c = comment(taskId, commentId);
        AuthenticatedUser user = CurrentUser.require();
        if (!Objects.equals(c.getCreatedBy(), user.userId()) && !user.isAdminOrOwner()) {
            throw ApiException.forbidden("Only the author or an admin can delete this comment");
        }
        c.markDeleted(Instant.now(clock), user.userId());
        if (c.getImageFileId() != null) {
            files.delete(c.getImageFileId());
        }
        Task t = require(taskId);
        Board board = boardService.lock(t.getBoardId());
        boardService.publish(board, boardService.commit(board), "TASK_COMMENT_DELETED",
                Map.of("taskId", taskId, "commentCount", comments.countByTaskId(taskId)));
    }

    // ------------------------------------------------------------------ helpers

    private TaskComment comment(UUID taskId, UUID commentId) {
        TaskComment c = comments.findById(commentId).orElseThrow(() -> ApiException.notFound("Comment"));
        if (!c.getTaskId().equals(taskId)) {
            throw ApiException.notFound("Comment");
        }
        return c;
    }

    private Task require(UUID id) {
        return tasks.findById(id).orElseThrow(() -> ApiException.notFound("Task"));
    }

    private void rehome(Task t, Parent parent) {
        Board target = boards.findByOwnerTypeAndOwnerId(parent.ownerType(), parent.ownerId())
                .orElseThrow(() -> ApiException.notFound("Board"));
        if (!target.getId().equals(t.getBoardId())) {
            Board source = boardService.lock(t.getBoardId());
            tasks.shift(t.getColumnId(), t.getPosition() + 1, Integer.MAX_VALUE, -1, t.getId());
            Board locked = boardService.lock(target.getId());
            BoardColumn column = locked.defaultColumn();
            t.place(locked.getId(), column.getId(), (int) tasks.countByColumnId(column.getId()) + 1);
        }
        t.reparent(parent.ownerType() == OwnerType.PROJECT ? parent.ownerId() : null,
                parent.ownerType() == OwnerType.JOB ? parent.ownerId() : null);
        if (!t.getAssigneeIds().isEmpty()) {
            t.replaceAssignees(validAssignees(parent.projectId(), t.getAssigneeIds()));
        }
    }

    private Parent resolveParent(UUID projectId, UUID jobId) {
        if (projectId != null && jobId != null) {
            throw ApiException.badRequest("TASK_PARENT_AMBIGUOUS", "A task belongs to a project or a job, not both");
        }
        if (projectId == null && jobId == null) {
            throw ApiException.badRequest("TASK_PARENT_REQUIRED", "projectId or jobId is required");
        }
        if (projectId != null) {
            if (!projects.existsById(projectId)) {
                throw ApiException.referenceNotFound("projectId");
            }
            return new Parent(OwnerType.PROJECT, projectId, projectId);
        }
        JobDirectory directory = jobs.getIfAvailable();
        JobDirectory.JobRef job = directory == null ? null : directory.job(jobId).orElse(null);
        if (job == null) {
            throw ApiException.referenceNotFound("jobId");
        }
        return new Parent(OwnerType.JOB, jobId, job.projectId());
    }

    /** Assignees must be team members of the parent project (EC-22). */
    private Set<UUID> validAssignees(UUID projectId, Set<UUID> requested) {
        if (requested == null || requested.isEmpty()) {
            return Set.of();
        }
        if (projectId == null) { // standalone service job: no project team to assign from
            throw ApiException.badRequest("ASSIGNEE_NOT_IN_TEAM", "Assignees must be members of the project team");
        }
        Set<UUID> team = teamMembers.findByProjectId(projectId).stream().map(TeamMember::getId)
                .collect(Collectors.toSet());
        if (!team.containsAll(requested)) {
            throw ApiException.badRequest("ASSIGNEE_NOT_IN_TEAM", "Assignees must be members of the project team");
        }
        return new HashSet<>(requested);
    }

    private void notifyAssigned(Task t, Set<UUID> before, Set<UUID> after) {
        Set<UUID> added = new HashSet<>(after);
        added.removeAll(before);
        if (added.isEmpty()) {
            return;
        }
        Set<UUID> userIds = teamMembers.findAllById(added).stream().map(TeamMember::getUserId)
                .collect(Collectors.toSet());
        events.publishEvent(new TaskAssigned(TenantContext.requireWorkspaceId(), t.getId(), t.getTitle(),
                t.getBoardId(), TenantContext.userId().orElse(null), userIds));
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "startDate and endDate are required");
        }
        if (end.isBefore(start)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "endDate must be on or after startDate");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }

    private static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw ApiException.badRequest("VALIDATION_ERROR", field + " is required");
        }
        return value;
    }

    private static Map<String, Object> snapshot(Task t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", t.getTitle());
        m.put("status", t.getStatus().name());
        m.put("priority", t.getPriority().name());
        m.put("startDate", t.getStartDate().toString());
        m.put("endDate", t.getEndDate().toString());
        m.put("assignees", t.getAssigneeIds().stream().map(UUID::toString).sorted().toList());
        return m;
    }

    private static TaskView view(Task t, BoardService.CardContext ctx) {
        return new TaskView(t.getId(), t.getProjectId(), t.getJobId(), t.getBoardId(), t.getColumnId(), t.getPosition(),
                t.getTitle(), t.getDescription(), t.getStartDate(), t.getEndDate(), t.getPriority().name(),
                t.getStatus().name(), t.getCompletedAt(), ctx.tagsOf(t), ctx.assigneesOf(t), t.getCreatedBy(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getVersion());
    }

    private record Parent(OwnerType ownerType, UUID ownerId, UUID projectId) {
    }

    public record TaskData(UUID projectId, UUID jobId, UUID columnId, String title, String description,
            LocalDate startDate, LocalDate endDate, String priority, String status, Set<UUID> tagIds,
            Set<UUID> assigneeTeamMemberIds) {
    }

    public record TaskFilter(UUID projectId, UUID jobId, String status, String priority, UUID assigneeId,
            LocalDate dueFrom, LocalDate dueTo, Boolean overdue, String q) {
    }

    /** 409 carrying the current board so the client can re-render (EC-16). */
    public static class BoardConflictException extends ApiException {
        public BoardConflictException(BoardViews.BoardView board) {
            super(org.springframework.http.HttpStatus.CONFLICT, "BOARD_CONFLICT",
                    "The board changed in the meantime. The latest version is included.", new HashMap<>(Map.of("board", board)));
        }
    }
}

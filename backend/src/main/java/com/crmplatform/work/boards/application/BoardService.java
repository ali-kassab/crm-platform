package com.crmplatform.work.boards.application;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.crm.LabelCatalog.LabelSummary;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.work.Boards;
import com.crmplatform.work.WorkEvents.BoardChanged;
import com.crmplatform.work.boards.application.BoardViews.Assignee;
import com.crmplatform.work.boards.application.BoardViews.BoardView;
import com.crmplatform.work.boards.application.BoardViews.ColumnView;
import com.crmplatform.work.boards.application.BoardViews.TaskCard;
import com.crmplatform.work.boards.domain.Board;
import com.crmplatform.work.boards.domain.BoardColumn;
import com.crmplatform.work.boards.domain.BoardRepositories.BoardRepository;
import com.crmplatform.work.boards.domain.BoardRepositories.BoardTemplateRepository;
import com.crmplatform.work.boards.domain.BoardRepositories.TaskCommentRepository;
import com.crmplatform.work.boards.domain.BoardRepositories.TaskRepository;
import com.crmplatform.work.boards.domain.BoardTemplate;
import com.crmplatform.work.boards.domain.Task;
import com.crmplatform.work.projects.domain.ProjectRepositories.TeamMemberRepository;
import com.crmplatform.work.projects.domain.TeamMember;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Boards, columns and board templates (FR-WRK-09..11, FR-WRK-18, BR-070..080). */
@Service
public class BoardService implements Boards {

    static final List<String> FALLBACK_COLUMNS = List.of("To Do", "In Progress", "Done");

    private final BoardRepository boards;
    private final BoardTemplateRepository templates;
    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final TeamMemberRepository teamMembers;
    private final LabelCatalog labels;
    private final WorkspaceMembers members;
    private final TaskService taskService;
    private final ApplicationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;

    public BoardService(BoardRepository boards, BoardTemplateRepository templates, TaskRepository tasks,
            TaskCommentRepository comments, TeamMemberRepository teamMembers, LabelCatalog labels,
            WorkspaceMembers members, @Lazy TaskService taskService, ApplicationEventPublisher events, AuditTrail audit,
            Clock clock) {
        this.boards = boards;
        this.templates = templates;
        this.tasks = tasks;
        this.comments = comments;
        this.teamMembers = teamMembers;
        this.labels = labels;
        this.members = members;
        this.taskService = taskService;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Boards (public API)

    @Override
    @Transactional
    public UUID createBoard(OwnerType ownerType, UUID ownerId, String title, UUID templateId) {
        Board board = new Board(ownerType, ownerId, title);
        BoardTemplate template = null;
        if (templateId != null) {
            template = templates.findById(templateId).orElseThrow(() -> ApiException.referenceNotFound("boardTemplateId"));
        } else {
            template = templates.findFirstByIsDefaultTrue().orElse(null);
        }
        if (template != null) {
            template.getColumns().forEach(c -> board.addColumn(c.getTitle(), c.isDefault()));
        } else {
            FALLBACK_COLUMNS.forEach(t -> board.addColumn(t, false)); // EC-12
        }
        return boards.save(board).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> boardOf(OwnerType ownerType, UUID ownerId) {
        return boards.findByOwnerTypeAndOwnerId(ownerType, ownerId).map(Board::getId);
    }

    @Override
    @Transactional
    public void deleteBoardOf(OwnerType ownerType, UUID ownerId) {
        boards.findByOwnerTypeAndOwnerId(ownerType, ownerId).ifPresent(board -> {
            Instant now = Instant.now(clock);
            UUID actor = TenantContext.userId().orElse(null);
            tasks.findByBoardIdOrderByPositionAsc(board.getId()).forEach(t -> t.markDeleted(now, actor));
            board.markDeleted(now, actor);
            events.publishEvent(new BoardChanged(TenantContext.requireWorkspaceId(), board.getId(),
                    board.getVersion() + 1, "BOARD_RESYNC", actor, Map.of()));
        });
    }

    @Override
    public UUID createTask(NewTask task) {
        return taskService.createFromAutomation(task);
    }

    // ------------------------------------------------------------------ board views

    @Transactional(readOnly = true)
    public BoardView view(UUID boardId) {
        Board board = boards.findById(boardId).orElseThrow(() -> ApiException.notFound("Board"));
        return render(board);
    }

    @Transactional(readOnly = true)
    public BoardView viewOf(OwnerType ownerType, UUID ownerId) {
        Board board = boards.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseThrow(() -> ApiException.notFound("Board"));
        return render(board);
    }

    BoardView render(Board board) {
        List<Task> boardTasks = tasks.findByBoardIdOrderByPositionAsc(board.getId());
        CardContext ctx = cardContext(boardTasks);
        Map<UUID, List<TaskCard>> byColumn = new HashMap<>();
        for (Task t : boardTasks) {
            byColumn.computeIfAbsent(t.getColumnId(), k -> new ArrayList<>()).add(ctx.card(t));
        }
        List<ColumnView> columns = board.getColumns().stream().map(c -> new ColumnView(c.getId(), c.getTitle(),
                c.getPosition(), c.isDefault(), byColumn.getOrDefault(c.getId(), List.of()))).toList();
        return new BoardView(board.getId(), board.getOwnerType().name(), board.getOwnerId(), board.getTitle(),
                board.getVersion(), columns);
    }

    CardContext cardContext(Collection<Task> list) {
        Set<UUID> tagIds = new HashSet<>();
        Set<UUID> memberIds = new HashSet<>();
        list.forEach(t -> {
            tagIds.addAll(t.getTagIds());
            memberIds.addAll(t.getAssigneeIds());
        });
        Map<UUID, LabelSummary> tagMap = labels.tags(tagIds);
        Map<UUID, TeamMember> memberMap = teamMembers.findAllById(memberIds).stream()
                .collect(Collectors.toMap(TeamMember::getId, Function.identity()));
        Map<UUID, WorkspaceMembers.MemberSummary> people = members.findAll(memberMap.values().stream()
                .map(TeamMember::getUserId).collect(Collectors.toSet()));
        Map<UUID, Long> commentCounts = new HashMap<>();
        if (!list.isEmpty()) {
            for (Object[] row : comments.countByTask(list.stream().map(Task::getId).toList())) {
                commentCounts.put((UUID) row[0], (Long) row[1]);
            }
        }
        return new CardContext(tagMap, memberMap, people, commentCounts);
    }

    record CardContext(Map<UUID, LabelSummary> tags, Map<UUID, TeamMember> teamMembers,
            Map<UUID, WorkspaceMembers.MemberSummary> people, Map<UUID, Long> commentCounts) {

        List<LabelSummary> tagsOf(Task t) {
            return t.getTagIds().stream().map(tags::get).filter(java.util.Objects::nonNull)
                    .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
        }

        List<Assignee> assigneesOf(Task t) {
            return t.getAssigneeIds().stream().map(teamMembers::get).filter(java.util.Objects::nonNull).map(tm -> {
                var p = people.get(tm.getUserId());
                return new Assignee(tm.getId(), tm.getUserId(), p == null ? "Former member" : p.fullName());
            }).toList();
        }

        TaskCard card(Task t) {
            return new TaskCard(t.getId(), t.getTitle(), t.getPriority().name(), t.getStatus().name(), t.getStartDate(),
                    t.getEndDate(), t.getPosition(), tagsOf(t), assigneesOf(t), commentCounts.getOrDefault(t.getId(), 0L));
        }
    }

    // ------------------------------------------------------------------ columns

    @Transactional
    public BoardView addColumn(UUID boardId, String title, boolean makeDefault) {
        Board board = lock(boardId);
        BoardColumn column = board.addColumn(title, false);
        if (makeDefault && !column.isDefault()) {
            switchDefault(board, column.getId());
        }
        long version = commit(board);
        publish(board, version, "COLUMN_CREATED", columnPayload(column));
        return render(board);
    }

    @Transactional
    public BoardView updateColumn(UUID boardId, UUID columnId, String title, Boolean makeDefault) {
        Board board = lock(boardId);
        if (title != null) {
            board.renameColumn(columnId, title);
        }
        if (Boolean.TRUE.equals(makeDefault) && !board.column(columnId).isDefault()) {
            switchDefault(board, columnId);
        }
        long version = commit(board);
        publish(board, version, "COLUMN_UPDATED", columnPayload(board.column(columnId)));
        return render(board);
    }

    @Transactional
    public BoardView moveColumn(UUID boardId, UUID columnId, int position) {
        Board board = lock(boardId);
        board.moveColumn(columnId, position);
        long version = commit(board);
        publish(board, version, "COLUMN_MOVED", Map.of("columnId", columnId,
                "position", board.column(columnId).getPosition()));
        return render(board);
    }

    /** Deletes a column; tasks must be relocated with {@code moveTasksTo} (BR-072, EC-18/19). */
    @Transactional
    public void deleteColumn(UUID boardId, UUID columnId, UUID moveTasksTo) {
        Board board = lock(boardId);
        board.column(columnId);
        List<Task> columnTasks = tasks.findByColumnIdOrderByPositionAsc(columnId);
        if (!columnTasks.isEmpty()) {
            if (moveTasksTo == null) {
                throw ApiException.conflict("COLUMN_NOT_EMPTY", "Move the column's tasks to another column first");
            }
            if (moveTasksTo.equals(columnId) || !board.hasColumn(moveTasksTo)) {
                throw ApiException.badRequest("COLUMN_NOT_ON_BOARD", "moveTasksTo must be another column of this board");
            }
            int next = (int) tasks.countByColumnId(moveTasksTo) + 1;
            for (Task t : columnTasks) {
                t.place(boardId, moveTasksTo, next++);
            }
            tasks.flush();
        }
        if (board.column(columnId).isDefault() && board.getColumns().size() > 1) {
            board.clearDefault(); // flushed first; removeColumn then marks the new default
            boards.flush();
        }
        board.removeColumn(columnId);
        long version = commit(board);
        Map<String, Object> payload = new HashMap<>();
        payload.put("columnId", columnId);
        if (moveTasksTo != null) {
            payload.put("movedTasksTo", moveTasksTo);
        }
        publish(board, version, "COLUMN_DELETED", payload);
    }

    // ------------------------------------------------------------------ templates

    @Transactional(readOnly = true)
    public Page<BoardTemplate> listTemplates(String q, Pageable pageable) {
        return templates.search(q, pageable);
    }

    @Transactional(readOnly = true)
    public BoardTemplate template(UUID id) {
        return templates.findById(id).orElseThrow(() -> ApiException.notFound("Board template"));
    }

    @Transactional
    public BoardTemplate createTemplate(String title, String description, List<String> columns, int defaultIndex,
            boolean makeDefault) {
        BoardTemplate t = templates.save(new BoardTemplate(title, description, columns, defaultIndex));
        if (makeDefault) {
            makeDefault(t);
        }
        audit.created("BOARD_TEMPLATE", t.getId(), null, null, Map.of("title", t.getTitle()));
        return t;
    }

    @Transactional
    public BoardTemplate updateTemplate(UUID id, String title, String description, List<String> columns,
            int defaultIndex, Boolean makeDefault) {
        BoardTemplate t = template(id);
        t.update(title, description, columns, defaultIndex);
        if (Boolean.TRUE.equals(makeDefault)) {
            makeDefault(t);
        } else if (Boolean.FALSE.equals(makeDefault)) {
            t.markDefault(false);
        }
        return t;
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        template(id).markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
    }

    private void makeDefault(BoardTemplate t) {
        templates.findByIsDefaultTrue().stream().filter(o -> !o.getId().equals(t.getId()))
                .forEach(o -> o.markDefault(false));
        templates.flush();
        t.markDefault(true);
    }

    /** Seeds the default template for a new workspace (AUTH_FLOWS F1). */
    @Transactional
    public void seedDefaultTemplate() {
        if (templates.count() == 0) {
            BoardTemplate t = templates.save(new BoardTemplate("Standard",
                    "To Do → In Progress → Review → Done", List.of("To Do", "In Progress", "Review", "Done"), 0));
            t.markDefault(true);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Two-phase default switch so the single-default index is never violated mid-flush. */
    private void switchDefault(Board board, UUID columnId) {
        board.column(columnId);
        board.clearDefault();
        boards.flush();
        board.setDefault(columnId);
    }

    Board lock(UUID boardId) {
        return boards.findByIdForUpdate(boardId).orElseThrow(() -> ApiException.notFound("Board"));
    }

    /** Touches and flushes the board so its version increments; returns the new version. */
    long commit(Board board) {
        board.touch(Instant.now(clock));
        boards.saveAndFlush(board);
        return board.getVersion();
    }

    void publish(Board board, long version, String type, Map<String, Object> payload) {
        events.publishEvent(new BoardChanged(TenantContext.requireWorkspaceId(), board.getId(), version, type,
                TenantContext.userId().orElse(null), payload));
    }

    private static Map<String, Object> columnPayload(BoardColumn c) {
        return Map.of("id", c.getId(), "title", c.getTitle(), "position", c.getPosition(), "isDefault", c.isDefault());
    }
}

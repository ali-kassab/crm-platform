package com.crmplatform.work.boards.web;

import com.crmplatform.work.boards.application.BoardService;
import com.crmplatform.work.boards.application.BoardViews.BoardView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

/** Boards and their columns (FR-WRK-09..11). */
@RestController
@RequestMapping("/api/v1/boards")
@Tag(name = "Boards")
public class BoardController {

    private final BoardService boards;

    public BoardController(BoardService boards) {
        this.boards = boards;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('work:read')")
    public BoardView get(@PathVariable UUID id) {
        return boards.view(id);
    }

    @PostMapping("/{id}/columns")
    @PreAuthorize("hasAuthority('work:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardView addColumn(@PathVariable UUID id, @Valid @RequestBody ColumnRequest body) {
        return boards.addColumn(id, body.title(), Boolean.TRUE.equals(body.isDefault()));
    }

    @PatchMapping("/{id}/columns/{columnId}")
    @PreAuthorize("hasAuthority('work:write')")
    public BoardView updateColumn(@PathVariable UUID id, @PathVariable UUID columnId,
            @Valid @RequestBody ColumnUpdateRequest body) {
        return boards.updateColumn(id, columnId, body.title(), body.isDefault());
    }

    @PostMapping("/{id}/columns/{columnId}/move")
    @PreAuthorize("hasAuthority('work:write')")
    public BoardView moveColumn(@PathVariable UUID id, @PathVariable UUID columnId,
            @Valid @RequestBody ColumnMoveRequest body) {
        return boards.moveColumn(id, columnId, body.position());
    }

    @DeleteMapping("/{id}/columns/{columnId}")
    @PreAuthorize("hasAuthority('work:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteColumn(@PathVariable UUID id, @PathVariable UUID columnId,
            @RequestParam(required = false) UUID moveTasksTo) {
        boards.deleteColumn(id, columnId, moveTasksTo);
    }

    public record ColumnRequest(@NotBlank @Size(max = 30) String title, Boolean isDefault) {
    }

    public record ColumnUpdateRequest(@Size(min = 1, max = 30) String title, Boolean isDefault) {
    }

    public record ColumnMoveRequest(@NotNull @Min(1) Integer position) {
    }
}

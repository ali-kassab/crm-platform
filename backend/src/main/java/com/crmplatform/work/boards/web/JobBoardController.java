package com.crmplatform.work.boards.web;

import com.crmplatform.work.Boards.OwnerType;
import com.crmplatform.work.boards.application.BoardService;
import com.crmplatform.work.boards.application.BoardViews.BoardView;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /jobs/{id}/board} (FR-WRK-09). Lives in work because boards are a work concept; the
 * board is soft-deleted with its job, so a deleted or foreign job yields 404.
 */
@RestController
@Tag(name = "Boards")
public class JobBoardController {

    private final BoardService boards;

    public JobBoardController(BoardService boards) {
        this.boards = boards;
    }

    @GetMapping("/api/v1/jobs/{id}/board")
    @PreAuthorize("hasAuthority('work:read')")
    public BoardView board(@PathVariable UUID id) {
        return boards.viewOf(OwnerType.JOB, id);
    }
}

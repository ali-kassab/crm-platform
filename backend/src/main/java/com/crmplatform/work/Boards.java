package com.crmplatform.work;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Public API of Kanban boards and tasks (used for job boards and automation CREATE_TASK). */
public interface Boards {

    enum OwnerType { PROJECT, JOB }

    /** Creates the owner's board from a template, the workspace default, or built-in columns (BR-054). */
    UUID createBoard(OwnerType ownerType, UUID ownerId, String title, UUID templateId);

    Optional<UUID> boardOf(OwnerType ownerType, UUID ownerId);

    /** Soft-deletes the board and its tasks. */
    void deleteBoardOf(OwnerType ownerType, UUID ownerId);

    /** Creates a task on the owner's board (column chosen by title, else default). */
    UUID createTask(NewTask task);

    record NewTask(OwnerType ownerType, UUID ownerId, UUID projectId, String title, String description,
            LocalDate startDate, LocalDate endDate, String priority, String columnTitle, Set<UUID> tagIds,
            List<UUID> assigneeTeamMemberIds) {
    }
}

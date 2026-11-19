package com.crmplatform.work.boards.application;

import com.crmplatform.crm.LabelCatalog.LabelSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read models returned by the board API. */
public final class BoardViews {

    private BoardViews() {
    }

    public record BoardView(UUID id, String ownerType, UUID ownerId, String title, long version,
            List<ColumnView> columns) {
    }

    public record ColumnView(UUID id, String title, int position, boolean isDefault, List<TaskCard> tasks) {
    }

    public record Assignee(UUID teamMemberId, UUID userId, String name) {
    }

    public record TaskCard(UUID id, String title, String priority, String status, LocalDate startDate,
            LocalDate endDate, int position, List<LabelSummary> tags, List<Assignee> assignees, long commentCount) {
    }

    public record TaskView(UUID id, UUID projectId, UUID jobId, UUID boardId, UUID columnId, int position, String title,
            String description, LocalDate startDate, LocalDate endDate, String priority, String status,
            Instant completedAt, List<LabelSummary> tags, List<Assignee> assignees, UUID createdBy, Instant createdAt,
            Instant updatedAt, long version) {
    }
}

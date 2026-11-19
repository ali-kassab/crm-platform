package com.crmplatform.work.boards.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

/** A task card on a board; belongs to exactly one project or job (BR-073..079). */
@Entity
@Table(name = "tasks")
@SQLRestriction("deleted_at is null")
public class Task extends TenantEntity {

    public enum Priority { LOW, MEDIUM, HIGH }

    public enum Status { PENDING, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED }

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "board_id", nullable = false)
    private UUID boardId;

    @Column(name = "column_id", nullable = false)
    private UUID columnId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 11)
    private Status status = Status.PENDING;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 200)
    @CollectionTable(name = "task_tags", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 200)
    @CollectionTable(name = "task_assignees", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "team_member_id")
    private Set<UUID> assigneeIds = new HashSet<>();

    protected Task() {
    }

    public Task(UUID projectId, UUID jobId, String title, String description, LocalDate startDate, LocalDate endDate) {
        this.projectId = projectId;
        this.jobId = jobId;
        this.title = title.trim();
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void place(UUID boardId, UUID columnId, int position) {
        this.boardId = boardId;
        this.columnId = columnId;
        this.position = position;
    }

    public void reparent(UUID projectId, UUID jobId) {
        this.projectId = projectId;
        this.jobId = jobId;
    }

    public void setTitle(String title) {
        this.title = title.trim();
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSchedule(LocalDate start, LocalDate end) {
        this.startDate = start;
        this.endDate = end;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    /** COMPLETED stamps {@code completedAt}; leaving COMPLETED clears it (BR-078). */
    public void setStatus(Status status, Instant now) {
        if (status == Status.COMPLETED && this.status != Status.COMPLETED) {
            this.completedAt = now;
        } else if (status != Status.COMPLETED) {
            this.completedAt = null;
        }
        this.status = status;
    }

    public void replaceTags(Set<UUID> tags) {
        this.tagIds.clear();
        this.tagIds.addAll(tags);
    }

    public void replaceAssignees(Set<UUID> assignees) {
        this.assigneeIds.clear();
        this.assigneeIds.addAll(assignees);
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getBoardId() {
        return boardId;
    }

    public UUID getColumnId() {
        return columnId;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Priority getPriority() {
        return priority;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Set<UUID> getTagIds() {
        return Set.copyOf(tagIds);
    }

    public Set<UUID> getAssigneeIds() {
        return Set.copyOf(assigneeIds);
    }
}

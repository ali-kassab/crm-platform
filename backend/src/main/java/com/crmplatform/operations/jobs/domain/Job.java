package com.crmplatform.operations.jobs.domain;

import com.crmplatform.shared.error.ApiException;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

/**
 * A work order (directive §5C): installation, preventive maintenance, corrective repair or
 * inspection for a customer at a site — inside a project (fit-out) or standalone (service, D2).
 * Every job knows its customer and site; assigned technicians are workspace users, so an
 * independent technician needs no project team.
 */
@Entity
@Table(name = "jobs")
@SQLRestriction("deleted_at is null")
public class Job extends TenantEntity {

    public enum Type { INSTALLATION, PREVENTIVE_MAINTENANCE, CORRECTIVE_REPAIR, INSPECTION }

    public enum Priority { LOW, NORMAL, HIGH, URGENT }

    public enum Status {
        NEW, SCHEDULED, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED;

        private static final Map<Status, Set<Status>> NEXT = Map.of(
                NEW, EnumSet.of(SCHEDULED, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED),
                SCHEDULED, EnumSet.of(NEW, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED),
                IN_PROGRESS, EnumSet.of(ON_HOLD, COMPLETED, CANCELLED),
                ON_HOLD, EnumSet.of(SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED),
                COMPLETED, EnumSet.of(IN_PROGRESS), // reopen
                CANCELLED, EnumSet.of(NEW)); // reopen

        public boolean canMoveTo(Status next) {
            return NEXT.get(this).contains(next);
        }

        public boolean isOpen() {
            return this != COMPLETED && this != CANCELLED;
        }
    }

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Type type = Type.CORRECTIVE_REPAIR;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.NORMAL;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    /** What the customer reported (complaint / issue), as entered. */
    @Column(name = "reported_issue", length = 2000)
    private String reportedIssue;

    /** Internal notes, never meant for the customer. */
    @Column(name = "internal_notes", length = 5000)
    private String internalNotes;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "estimated_end_date")
    private LocalDate estimatedEndDate;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal budget = BigDecimal.ZERO;

    /** A team member (of the project) managing this job — project jobs only. */
    @Column(name = "team_manager_member_id")
    private UUID teamManagerMemberId;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "job_team_members", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "team_member_id")
    private Set<UUID> memberIds = new HashSet<>();

    /** Assigned technicians (workspace users); works with or without a project team. */
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "job_assignees", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "user_id")
    private Set<UUID> assigneeUserIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "job_tags", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    protected Job() {
    }

    /** {@code projectId} may be null (standalone service job); customer and site are required. */
    public Job(UUID projectId, UUID contactId, UUID locationId, String title, String description) {
        this.projectId = projectId;
        this.contactId = contactId;
        this.locationId = locationId;
        setTitle(title);
        this.description = description == null ? "" : description;
    }

    public void setTitle(String title) {
        this.title = title.trim();
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public void classify(Type type, Priority priority) {
        this.type = type;
        this.priority = priority;
    }

    public void setReportedIssue(String reportedIssue) {
        this.reportedIssue = reportedIssue;
    }

    public void setInternalNotes(String internalNotes) {
        this.internalNotes = internalNotes;
    }

    public void setSite(UUID contactId, UUID locationId) {
        this.contactId = contactId;
        this.locationId = locationId;
    }

    public void setSchedule(LocalDate start, LocalDate estimatedEnd) {
        this.startDate = start;
        this.estimatedEndDate = estimatedEnd;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public void setTeam(UUID managerMemberId, Set<UUID> members) {
        this.teamManagerMemberId = managerMemberId;
        this.memberIds.clear();
        this.memberIds.addAll(members);
    }

    public void assign(Set<UUID> userIds) {
        this.assigneeUserIds.clear();
        this.assigneeUserIds.addAll(userIds);
    }

    public void replaceTags(Set<UUID> tags) {
        this.tagIds.clear();
        this.tagIds.addAll(tags);
    }

    /**
     * Moves through the allowed lifecycle; records the first actual start and the completion time
     * (cleared again if the job is reopened).
     */
    public void moveTo(Status next, Instant now) {
        if (next == status) {
            return;
        }
        if (!status.canMoveTo(next)) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION",
                    "This job cannot move from " + status + " to " + next);
        }
        if (next == Status.IN_PROGRESS && actualStartAt == null) {
            actualStartAt = now;
        }
        completedAt = next == Status.COMPLETED ? now : null;
        status = next;
    }

    public boolean isAssignedTo(UUID userId) {
        return assigneeUserIds.contains(userId);
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getContactId() {
        return contactId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public Type getType() {
        return type;
    }

    public Status getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getReportedIssue() {
        return reportedIssue;
    }

    public String getInternalNotes() {
        return internalNotes;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEstimatedEndDate() {
        return estimatedEndDate;
    }

    public Instant getActualStartAt() {
        return actualStartAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public UUID getTeamManagerMemberId() {
        return teamManagerMemberId;
    }

    public Set<UUID> getMemberIds() {
        return Set.copyOf(memberIds);
    }

    public Set<UUID> getAssigneeUserIds() {
        return Set.copyOf(assigneeUserIds);
    }

    public Set<UUID> getTagIds() {
        return Set.copyOf(tagIds);
    }
}

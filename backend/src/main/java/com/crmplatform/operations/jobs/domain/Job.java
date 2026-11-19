package com.crmplatform.operations.jobs.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

/** A unit of delivery inside a project, with its own board and budget (BR-060..063). */
@Entity
@Table(name = "jobs")
@SQLRestriction("deleted_at is null")
public class Job extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "estimated_end_date", nullable = false)
    private LocalDate estimatedEndDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal budget;

    /** A team member (of the project) managing this job. */
    @Column(name = "team_manager_member_id")
    private UUID teamManagerMemberId;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "job_team_members", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "team_member_id")
    private Set<UUID> memberIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "job_tags", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    protected Job() {
    }

    public Job(UUID projectId, String title, String description) {
        this.projectId = projectId;
        setTitle(title);
        this.description = description;
    }

    public void setTitle(String title) {
        this.title = title.trim();
    }

    public void setDescription(String description) {
        this.description = description;
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

    public void replaceTags(Set<UUID> tags) {
        this.tagIds.clear();
        this.tagIds.addAll(tags);
    }

    public UUID getProjectId() {
        return projectId;
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

    public LocalDate getEstimatedEndDate() {
        return estimatedEndDate;
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

    public Set<UUID> getTagIds() {
        return Set.copyOf(tagIds);
    }
}

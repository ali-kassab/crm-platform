package com.crmplatform.work.projects.domain;

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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

/** A workspace member staffed on a project (BR-052, BR-053). */
@Entity
@Table(name = "team_members")
@SQLRestriction("deleted_at is null")
public class TeamMember extends TenantEntity {

    public enum Responsibility { PROJECT_PLANNING, TEAM_MANAGEMENT, RESOURCE_ALLOCATION, REPORTING }

    public static final String MANAGER_POSITION = "Project Manager";
    public static final String DEFAULT_POSITION = "Team Member";

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String position;

    @Column(name = "is_manager", nullable = false)
    private boolean manager;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "team_member_responsibilities", joinColumns = @JoinColumn(name = "team_member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "responsibility")
    private Set<Responsibility> responsibilities = new HashSet<>();

    protected TeamMember() {
    }

    public TeamMember(UUID projectId, UUID userId, String position, boolean manager) {
        this.projectId = projectId;
        this.userId = userId;
        this.manager = manager;
        this.position = position == null || position.isBlank()
                ? (manager ? MANAGER_POSITION : DEFAULT_POSITION) : position.trim();
    }

    public void promoteToManager() {
        this.manager = true;
        if (DEFAULT_POSITION.equals(position)) {
            this.position = MANAGER_POSITION;
        }
    }

    public void demoteFromManager() {
        this.manager = false;
        if (MANAGER_POSITION.equals(position)) {
            this.position = DEFAULT_POSITION;
        }
    }

    public void setPosition(String position) {
        this.position = position == null || position.isBlank()
                ? (manager ? MANAGER_POSITION : DEFAULT_POSITION) : position.trim();
    }

    public void setResponsibilities(Set<Responsibility> values) {
        this.responsibilities.clear();
        if (values != null && !values.isEmpty()) {
            this.responsibilities.addAll(EnumSet.copyOf(values));
        }
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPosition() {
        return position;
    }

    public boolean isManager() {
        return manager;
    }

    public Set<Responsibility> getResponsibilities() {
        return Set.copyOf(responsibilities);
    }
}

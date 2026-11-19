package com.crmplatform.work.projects.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "team_member_notes")
@SQLRestriction("deleted_at is null")
public class TeamMemberNote extends TenantEntity {

    @Column(name = "team_member_id", nullable = false, updatable = false)
    private UUID teamMemberId;

    @Column(nullable = false, length = 2000)
    private String text;

    protected TeamMemberNote() {
    }

    public TeamMemberNote(UUID teamMemberId, String text) {
        this.teamMemberId = teamMemberId;
        this.text = text.trim();
    }

    public UUID getTeamMemberId() {
        return teamMemberId;
    }

    public String getText() {
        return text;
    }
}

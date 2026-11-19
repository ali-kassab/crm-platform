package com.crmplatform.milestones.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/** Reusable milestone definition (≥ 2 steps, BR-041); steps stored as JSON. */
@Entity
@Table(name = "milestone_templates")
@SQLRestriction("deleted_at is null")
public class MilestoneTemplate extends TenantEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TemplateStep> steps = new ArrayList<>();

    protected MilestoneTemplate() {
    }

    public MilestoneTemplate(String name, String description, UUID categoryId, List<TemplateStep> steps) {
        update(name, description, categoryId, steps);
    }

    public void update(String name, String description, UUID categoryId, List<TemplateStep> steps) {
        this.name = name.trim();
        this.description = description;
        this.categoryId = categoryId;
        this.steps = new ArrayList<>(steps);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public List<TemplateStep> getSteps() {
        return List.copyOf(steps);
    }

    public record TemplateStep(int order, String label, String icon, String backgroundColor, String textColor,
            String viewMode, UUID imageFileId, LocalDate startDate, LocalDate endDate) {
    }
}

package com.crmplatform.milestones.domain;

import com.crmplatform.milestones.MilestoneOwner.OwnerType;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * Ordered steps tracking an entity's progress (BR-040..044). {@code currentStep} is the 0-based
 * index of the first incomplete step (or the last index when all are complete).
 */
@Entity
@Table(name = "milestones")
@SQLRestriction("deleted_at is null")
public class Milestone extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, updatable = false, length = 10)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "milestone_id", nullable = false)
    @OrderBy("stepOrder asc")
    private List<MilestoneStep> steps = new ArrayList<>();

    protected Milestone() {
    }

    public Milestone(OwnerType ownerType, UUID ownerId) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
    }

    public void define(String name, String description, UUID categoryId, List<MilestoneStep> newSteps) {
        if (newSteps.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A milestone needs at least one step");
        }
        long distinct = newSteps.stream().map(MilestoneStep::getStepOrder).distinct().count();
        if (distinct != newSteps.size()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Step orders must be unique within a milestone");
        }
        this.name = name.trim();
        this.description = description;
        this.categoryId = categoryId;
        this.steps.clear();
        newSteps.stream().sorted((a, b) -> Integer.compare(a.getStepOrder(), b.getStepOrder())).forEach(steps::add);
        recalculateCurrentStep();
    }

    public void complete(UUID stepId, UUID by, Instant at) {
        MilestoneStep step = step(stepId);
        if (step.isCompleted()) {
            throw ApiException.conflict("ALREADY_COMPLETED", "This step is already completed");
        }
        step.complete(by, at);
        recalculateCurrentStep();
    }

    public void completeByOrder(int order, UUID by, Instant at) {
        MilestoneStep step = steps.stream().filter(s -> s.getStepOrder() == order).findFirst()
                .orElseThrow(() -> ApiException.notFound("Milestone step"));
        if (!step.isCompleted()) {
            step.complete(by, at);
            recalculateCurrentStep();
        }
    }

    public void reopen(UUID stepId) {
        step(stepId).reopen();
        recalculateCurrentStep();
    }

    /** Progress % = completed ÷ total, rounded (BR-043). */
    public int progress() {
        if (steps.isEmpty()) {
            return 0;
        }
        long done = steps.stream().filter(MilestoneStep::isCompleted).count();
        return (int) Math.round(done * 100.0 / steps.size());
    }

    private void recalculateCurrentStep() {
        int index = 0;
        while (index < steps.size() && steps.get(index).isCompleted()) {
            index++;
        }
        this.currentStep = Math.min(index, Math.max(0, steps.size() - 1));
    }

    private MilestoneStep step(UUID stepId) {
        return steps.stream().filter(s -> s.getId().equals(stepId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Milestone step"));
    }

    public OwnerType getOwnerType() {
        return ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
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

    public int getCurrentStep() {
        return currentStep;
    }

    public List<MilestoneStep> getSteps() {
        return List.copyOf(steps);
    }
}

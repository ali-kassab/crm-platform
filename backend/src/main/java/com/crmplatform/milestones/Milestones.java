package com.crmplatform.milestones;

import com.crmplatform.milestones.MilestoneOwner.OwnerType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API used by owning modules (and automation commands). */
public interface Milestones {

    Optional<MilestoneView> find(OwnerType ownerType, UUID ownerId);

    MilestoneView set(OwnerType ownerType, UUID ownerId, MilestoneInput input);

    MilestoneView setFromTemplate(OwnerType ownerType, UUID ownerId, UUID templateId);

    void clear(OwnerType ownerType, UUID ownerId);

    MilestoneView completeStepByOrder(OwnerType ownerType, UUID ownerId, int stepOrder);

    record StepInput(Integer order, String label, String icon, String backgroundColor, String textColor,
            String viewMode, UUID imageFileId, LocalDate startDate, LocalDate endDate) {
    }

    record MilestoneInput(String name, String description, UUID categoryId, List<StepInput> steps) {
    }

    record StepView(UUID id, int order, String label, String icon, String backgroundColor, String textColor,
            String viewMode, UUID imageFileId, LocalDate startDate, LocalDate endDate, boolean completed,
            Instant completedAt, UUID completedBy) {
    }

    record MilestoneView(UUID id, String ownerType, UUID ownerId, String name, String description, UUID categoryId,
            int currentStep, int progress, List<StepView> steps, long version) {
    }
}

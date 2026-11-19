package com.crmplatform.milestones.domain;

import com.crmplatform.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "milestone_steps")
public class MilestoneStep {

    @Id
    private UUID id = UuidV7.generate();

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 40)
    private String icon = "check-check";

    @Column(name = "background_color", nullable = false, length = 7)
    private String backgroundColor = "#000000";

    @Column(name = "text_color", nullable = false, length = 7)
    private String textColor = "#FFFFFF";

    @Column(name = "view_mode", nullable = false, length = 5)
    private String viewMode = "ICON";

    @Column(name = "image_file_id")
    private UUID imageFileId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    protected MilestoneStep() {
    }

    public MilestoneStep(int stepOrder, String label, String icon, String backgroundColor, String textColor,
            String viewMode, UUID imageFileId, LocalDate startDate, LocalDate endDate) {
        this.stepOrder = stepOrder;
        this.label = label.trim();
        this.icon = icon == null ? "check-check" : icon;
        this.backgroundColor = backgroundColor == null ? "#000000" : backgroundColor.toUpperCase();
        this.textColor = textColor == null ? "#FFFFFF" : textColor.toUpperCase();
        this.viewMode = viewMode == null ? "ICON" : viewMode;
        this.imageFileId = imageFileId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    void complete(UUID by, Instant at) {
        this.completed = true;
        this.completedAt = at;
        this.completedBy = by;
    }

    void reopen() {
        this.completed = false;
        this.completedAt = null;
        this.completedBy = null;
    }

    public UUID getId() {
        return id;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public String getViewMode() {
        return viewMode;
    }

    public UUID getImageFileId() {
        return imageFileId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }
}

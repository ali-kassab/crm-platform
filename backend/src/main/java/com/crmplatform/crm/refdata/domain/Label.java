package com.crmplatform.crm.refdata.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/** Common shape of statuses and tags: a coloured, categorised label that can trigger automation. */
@MappedSuperclass
public abstract class Label extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(length = 500)
    private String description;

    @Column(name = "background_color", nullable = false, length = 7)
    private String backgroundColor = "#000000";

    @Column(name = "text_color", nullable = false, length = 7)
    private String textColor = "#FFFFFF";

    @Column(nullable = false, length = 40)
    private String icon = "check-check";

    @Column(name = "start_procedure_id")
    private UUID startProcedureId;

    @Column(name = "stop_procedure_id")
    private UUID stopProcedureId;

    protected Label() {
    }

    public void update(LabelValues values) {
        this.name = values.name().trim();
        this.categoryId = values.categoryId();
        this.description = values.description();
        this.backgroundColor = values.backgroundColor() == null ? "#000000" : values.backgroundColor().toUpperCase();
        this.textColor = values.textColor() == null ? "#FFFFFF" : values.textColor().toUpperCase();
        this.icon = values.icon() == null ? "check-check" : values.icon();
        this.startProcedureId = values.startProcedureId();
        this.stopProcedureId = values.stopProcedureId();
    }

    /** Removes a procedure binding (used when a procedure is force-deleted). */
    public void unbindProcedure(UUID procedureId) {
        if (procedureId.equals(startProcedureId)) {
            startProcedureId = null;
        }
        if (procedureId.equals(stopProcedureId)) {
            stopProcedureId = null;
        }
    }

    public String getName() {
        return name;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getDescription() {
        return description;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public String getIcon() {
        return icon;
    }

    public UUID getStartProcedureId() {
        return startProcedureId;
    }

    public UUID getStopProcedureId() {
        return stopProcedureId;
    }

    public record LabelValues(String name, UUID categoryId, String description, String backgroundColor,
            String textColor, String icon, UUID startProcedureId, UUID stopProcedureId) {
    }
}

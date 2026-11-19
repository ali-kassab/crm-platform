package com.crmplatform.work.boards.domain;

import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.SQLRestriction;

/** Reusable column layout for new boards; at most one default per workspace (BR-080). */
@Entity
@Table(name = "board_templates")
@SQLRestriction("deleted_at is null")
public class BoardTemplate extends TenantEntity {

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "board_template_columns", joinColumns = @JoinColumn(name = "template_id"))
    @OrderBy("position asc")
    private List<TemplateColumn> columns = new ArrayList<>();

    protected BoardTemplate() {
    }

    public BoardTemplate(String title, String description, List<String> columnTitles, int defaultIndex) {
        update(title, description, columnTitles, defaultIndex);
    }

    public void update(String title, String description, List<String> columnTitles, int defaultIndex) {
        if (columnTitles == null || columnTitles.isEmpty() || columnTitles.size() > Board.MAX_COLUMNS) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A template needs 1 to 20 columns");
        }
        this.title = title.trim();
        this.description = description;
        this.columns.clear();
        int safeDefault = defaultIndex < 0 || defaultIndex >= columnTitles.size() ? 0 : defaultIndex;
        for (int i = 0; i < columnTitles.size(); i++) {
            columns.add(new TemplateColumn(columnTitles.get(i).trim(), i + 1, i == safeDefault));
        }
    }

    public void markDefault(boolean value) {
        this.isDefault = value;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public List<TemplateColumn> getColumns() {
        return List.copyOf(columns);
    }

    @Embeddable
    public static class TemplateColumn {

        @Column(nullable = false, length = 30)
        private String title;

        @Column(nullable = false)
        private int position;

        @Column(name = "is_default", nullable = false)
        private boolean isDefault;

        protected TemplateColumn() {
        }

        TemplateColumn(String title, int position, boolean isDefault) {
            this.title = title;
            this.position = position;
            this.isDefault = isDefault;
        }

        public String getTitle() {
            return title;
        }

        public int getPosition() {
            return position;
        }

        public boolean isDefault() {
            return isDefault;
        }
    }
}

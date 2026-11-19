package com.crmplatform.work.flowcharts;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/** A flowchart with its diagram stored and edited in-app (BR-085, NF-01). */
@Entity
@Table(name = "flowcharts")
@SQLRestriction("deleted_at is null")
public class Flowchart extends TenantEntity {

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "thumbnail_file_id")
    private UUID thumbnailFileId;

    @Column(nullable = false)
    private boolean favorite;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> diagram = Map.of("nodes", java.util.List.of(), "edges", java.util.List.of());

    @Column(name = "diagram_size", nullable = false)
    private int diagramSize;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "flowchart_tags", joinColumns = @JoinColumn(name = "flowchart_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    protected Flowchart() {
    }

    public Flowchart(String title, UUID ownerUserId) {
        this.title = title.trim();
        this.ownerUserId = ownerUserId;
    }

    public void setTitle(String title) {
        this.title = title.trim();
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setThumbnailFileId(UUID thumbnailFileId) {
        this.thumbnailFileId = thumbnailFileId;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public void replaceTags(Set<UUID> tags) {
        this.tagIds.clear();
        this.tagIds.addAll(tags);
    }

    public void setDiagram(Map<String, Object> diagram, int sizeBytes) {
        this.diagram = diagram;
        this.diagramSize = sizeBytes;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getThumbnailFileId() {
        return thumbnailFileId;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public Map<String, Object> getDiagram() {
        return diagram;
    }

    public int getDiagramSize() {
        return diagramSize;
    }

    public Set<UUID> getTagIds() {
        return Set.copyOf(tagIds);
    }
}

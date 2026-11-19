package com.crmplatform.work.boards.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "task_comments")
@SQLRestriction("deleted_at is null")
public class TaskComment extends TenantEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(name = "image_file_id")
    private UUID imageFileId;

    protected TaskComment() {
    }

    public TaskComment(UUID taskId, String text, UUID imageFileId) {
        this.taskId = taskId;
        this.text = text.trim();
        this.imageFileId = imageFileId;
    }

    public void edit(String text) {
        this.text = text.trim();
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getText() {
        return text;
    }

    public UUID getImageFileId() {
        return imageFileId;
    }
}

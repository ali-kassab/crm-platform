package com.crmplatform.crm.notes.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/** A note on exactly one contact, project or job (BR-030..032). */
@Entity
@Table(name = "notes")
@SQLRestriction("deleted_at is null")
public class Note extends TenantEntity {

    @Column(name = "contact_id", updatable = false)
    private UUID contactId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "job_id", updatable = false)
    private UUID jobId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 5000)
    private String body;

    @Column(name = "document_file_id")
    private UUID documentFileId;

    protected Note() {
    }

    public Note(UUID contactId, UUID projectId, UUID jobId, String title, String body) {
        this.contactId = contactId;
        this.projectId = projectId;
        this.jobId = jobId;
        this.title = title.trim();
        this.body = body.trim();
    }

    public void edit(String title, String body) {
        this.title = title.trim();
        this.body = body.trim();
    }

    public void setDocumentFileId(UUID documentFileId) {
        this.documentFileId = documentFileId;
    }

    public UUID getContactId() {
        return contactId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public UUID getDocumentFileId() {
        return documentFileId;
    }
}

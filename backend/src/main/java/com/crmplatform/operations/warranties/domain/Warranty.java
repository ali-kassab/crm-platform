package com.crmplatform.operations.warranties.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

/** A provider warranty, optionally tied to a project and/or job (BR-064, BR-065). */
@Entity
@Table(name = "warranties")
@SQLRestriction("deleted_at is null")
public class Warranty extends TenantEntity {

    public enum Status { ACTIVE, EXPIRED, CANCELLED }

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    /** Remind this many days before the end date (0 = no reminder). */
    @Column(name = "reminder_days", nullable = false)
    private int reminderDays = 30;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 150)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    @Column(length = 2000)
    private String note;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "warranty_tags", joinColumns = @JoinColumn(name = "warranty_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    protected Warranty() {
    }

    public Warranty(String title, String provider) {
        setTitle(title);
        setProvider(provider);
    }

    public void setTitle(String title) {
        this.title = title.trim();
    }

    public void setProvider(String provider) {
        this.provider = provider.trim();
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public void setPeriod(LocalDate start, LocalDate end) {
        if (endDate != null && !endDate.equals(end)) {
            reminderSentAt = null; // extended or shortened: remind again for the new end date
        }
        this.startDate = start;
        this.endDate = end;
    }

    public void link(UUID projectId, UUID jobId) {
        this.projectId = projectId;
        this.jobId = jobId;
    }

    public void attachTo(UUID contactId, UUID locationId, UUID equipmentId) {
        this.contactId = contactId;
        this.locationId = locationId;
        this.equipmentId = equipmentId;
    }

    public void setReminderDays(int days) {
        if (days != reminderDays) {
            reminderSentAt = null; // a new reminder window
        }
        this.reminderDays = days;
    }

    public void reminderSent(Instant at) {
        this.reminderSentAt = at;
    }

    public UUID getContactId() {
        return contactId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public UUID getEquipmentId() {
        return equipmentId;
    }

    public int getReminderDays() {
        return reminderDays;
    }

    public Instant getReminderSentAt() {
        return reminderSentAt;
    }

    public void replaceTags(Set<UUID> tags) {
        this.tagIds.clear();
        this.tagIds.addAll(tags);
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

    public String getProvider() {
        return provider;
    }

    public Status getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Set<UUID> getTagIds() {
        return Set.copyOf(tagIds);
    }
}

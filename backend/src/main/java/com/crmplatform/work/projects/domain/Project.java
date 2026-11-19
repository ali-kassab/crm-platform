package com.crmplatform.work.projects.domain;

import com.crmplatform.shared.persistence.Address;
import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

/** A delivery project at a customer location (BR-050..057). */
@Entity
@Table(name = "projects")
@SQLRestriction("deleted_at is null")
public class Project extends TenantEntity {

    public enum Type { COMMERCIAL, RESIDENTIAL, INDUSTRIAL, INSTITUTIONAL, INFRASTRUCTURE }

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Type type = Type.COMMERCIAL;

    @Embedded
    private Address address;

    @Column(name = "total_area", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalArea;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal budget;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "estimated_end_date", nullable = false)
    private LocalDate estimatedEndDate;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "project_tags", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    protected Project() {
    }

    public Project(String name, String description, Type type) {
        this.name = name.trim();
        this.description = description;
        this.type = type;
    }

    public void setName(String name) {
        this.name = name.trim();
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public void setTotalArea(BigDecimal totalArea) {
        this.totalArea = totalArea;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public void setSchedule(LocalDate start, LocalDate estimatedEnd) {
        this.startDate = start;
        this.estimatedEndDate = estimatedEnd;
    }

    public void setLocation(UUID locationId, UUID contactId) {
        this.locationId = locationId;
        this.contactId = contactId;
    }

    public void replaceTags(Set<UUID> tags) {
        this.tagIds.clear();
        this.tagIds.addAll(tags);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Type getType() {
        return type;
    }

    public Address getAddress() {
        return address;
    }

    public BigDecimal getTotalArea() {
        return totalArea;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEstimatedEndDate() {
        return estimatedEndDate;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public UUID getContactId() {
        return contactId;
    }

    public Set<UUID> getTagIds() {
        return Set.copyOf(tagIds);
    }
}

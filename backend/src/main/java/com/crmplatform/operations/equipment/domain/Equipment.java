package com.crmplatform.operations.equipment.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * A device installed at a customer site (directive §5H): cameras, recorders, access-control panels,
 * HVAC units… Its service history is the jobs that worked on it, and its warranties link to it.
 */
@Entity
@Table(name = "equipment")
@SQLRestriction("deleted_at is null")
public class Equipment extends TenantEntity {

    public enum Category {
        CCTV_CAMERA, RECORDER, ACCESS_CONTROL, ALARM, FIRE_SYSTEM, NETWORK, HVAC_UNIT, ELECTRICAL, PLUMBING, OTHER
    }

    public enum Status { ACTIVE, FAULTY, REPLACED, REMOVED }

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "installed_job_id")
    private UUID installedJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String manufacturer;

    @Column(length = 100)
    private String model;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "installed_on")
    private LocalDate installedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    @Column(length = 2000)
    private String notes;

    protected Equipment() {
    }

    public Equipment(UUID contactId, UUID locationId, Category category, String name) {
        placeAt(contactId, locationId);
        this.category = category;
        setName(name);
    }

    public void placeAt(UUID contactId, UUID locationId) {
        this.contactId = contactId;
        this.locationId = locationId;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public void setName(String name) {
        this.name = name.trim();
    }

    public void describe(String manufacturer, String model, String serialNumber) {
        this.manufacturer = blankToNull(manufacturer);
        this.model = blankToNull(model);
        this.serialNumber = blankToNull(serialNumber);
    }

    public void setInstallation(LocalDate installedOn, UUID installedJobId) {
        this.installedOn = installedOn;
        this.installedJobId = installedJobId;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public UUID getContactId() {
        return contactId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public UUID getInstalledJobId() {
        return installedJobId;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public LocalDate getInstalledOn() {
        return installedOn;
    }

    public Status getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}

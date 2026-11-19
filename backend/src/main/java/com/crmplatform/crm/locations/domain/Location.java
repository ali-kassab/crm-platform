package com.crmplatform.crm.locations.domain;

import com.crmplatform.shared.persistence.Address;
import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/** A customer site (BR-025..027). */
@Entity
@Table(name = "locations")
@SQLRestriction("deleted_at is null")
public class Location extends TenantEntity {

    public enum Type { RESIDENTIAL, COMMERCIAL, INDUSTRIAL, RETAIL, WAREHOUSE, OFFICE }

    public static final java.util.List<String> WEEKDAYS =
            java.util.List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Type type;

    @Column(nullable = false, length = 150)
    private String name;

    @Embedded
    private Address address;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "access_instructions", length = 2000)
    private String accessInstructions;

    @Column(name = "parking_available", nullable = false)
    private boolean parkingAvailable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working_hours", nullable = false, columnDefinition = "jsonb")
    private Map<String, DayHours> workingHours = defaultHours();

    protected Location() {
    }

    public Location(UUID contactId, Type type, String name) {
        this.contactId = contactId;
        this.type = type;
        this.name = name.trim();
    }

    /** Mon–Fri 09:00–17:00, weekends closed (BR-026). */
    public static Map<String, DayHours> defaultHours() {
        Map<String, DayHours> hours = new LinkedHashMap<>();
        for (String day : WEEKDAYS) {
            boolean weekend = day.startsWith("S");
            hours.put(day, new DayHours("09:00", "17:00", weekend));
        }
        return hours;
    }

    public void setContactId(UUID contactId) {
        this.contactId = contactId;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setName(String name) {
        this.name = name.trim();
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public void setCoordinates(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void setAccessInstructions(String accessInstructions) {
        this.accessInstructions = accessInstructions;
    }

    public void setParkingAvailable(boolean parkingAvailable) {
        this.parkingAvailable = parkingAvailable;
    }

    public void setWorkingHours(Map<String, DayHours> workingHours) {
        this.workingHours = new LinkedHashMap<>(workingHours);
    }

    public UUID getContactId() {
        return contactId;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Address getAddress() {
        return address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getAccessInstructions() {
        return accessInstructions;
    }

    public boolean isParkingAvailable() {
        return parkingAvailable;
    }

    public Map<String, DayHours> getWorkingHours() {
        return Map.copyOf(workingHours);
    }

    public record DayHours(String open, String close, boolean closed) {
    }
}

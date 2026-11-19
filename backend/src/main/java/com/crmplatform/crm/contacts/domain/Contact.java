package com.crmplatform.crm.contacts.domain;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contacts")
@SQLRestriction("deleted_at is null")
public class Contact extends TenantEntity {

    public enum Type { CLIENT, PROSPECT, PARTNER, LEAD }

    /** An individual customer, or a company (whose person name is an optional contact person). */
    public enum Kind { INDIVIDUAL, COMPANY }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind = Kind.INDIVIDUAL;

    @Column(name = "name_title", length = 10)
    private String title;

    @Column(name = "first_name", length = 60)
    private String firstName;

    @Column(name = "middle_name", length = 60)
    private String middleName;

    @Column(name = "last_name", length = 60)
    private String lastName;

    @Column(length = 150)
    private String company;

    @Column(length = 100)
    private String industry;

    @Column(length = 100)
    private String position;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 20)
    private String mobile;

    @Column(name = "work_phone", length = 20)
    private String workPhone;

    @Embedded
    private Address address;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "image_file_id")
    private UUID imageFileId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "status_id")
    private UUID statusId;

    @Column(name = "representative_user_id")
    private UUID representativeUserId;

    @Column(name = "contract_signed", nullable = false)
    private boolean contractSigned;

    @Column(nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "contact_tags", joinColumns = @JoinColumn(name = "contact_id"))
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    protected Contact() {
    }

    /** Names, organisation and channels are set by the caller, which validates the kind's rules. */
    public Contact(Type type, Kind kind) {
        this.type = type;
        this.kind = kind;
    }

    // ----------------------------------------------------------------- mutators (used by ContactService)

    public void setType(Type type) {
        this.type = type;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public void setName(String title, String first, String middle, String last) {
        this.title = title;
        this.firstName = trimToNull(first);
        this.middleName = trimToNull(middle);
        this.lastName = trimToNull(last);
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public void setOrganisation(String company, String industry, String position) {
        this.company = company;
        this.industry = industry;
        this.position = position;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhones(String phone, String mobile, String workPhone) {
        this.phone = phone;
        this.mobile = mobile;
        this.workPhone = workPhone;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public void setCoordinates(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void setImageFileId(UUID imageFileId) {
        this.imageFileId = imageFileId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public void setStatusId(UUID statusId) {
        this.statusId = statusId;
    }

    public void setRepresentativeUserId(UUID representativeUserId) {
        this.representativeUserId = representativeUserId;
    }

    public void setContractSigned(boolean contractSigned) {
        this.contractSigned = contractSigned;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = new HashMap<>(metadata);
    }

    public void replaceTags(Set<UUID> tags) {
        this.tagIds.clear();
        this.tagIds.addAll(tags);
    }

    // ----------------------------------------------------------------- getters

    /** The person's name (first, middle, last — whichever exist), or null if there is none. */
    public String personName() {
        String name = String.join(" ", java.util.stream.Stream.of(firstName, middleName, lastName)
                .filter(java.util.Objects::nonNull).toList());
        return name.isEmpty() ? null : name;
    }

    /**
     * How the customer is shown everywhere: an individual by name; a company by its company name.
     * Kept as {@code fullName} for API compatibility.
     */
    public String fullName() {
        if (kind == Kind.COMPANY) {
            return company;
        }
        return personName();
    }

    public Type getType() {
        return type;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getCompany() {
        return company;
    }

    public String getIndustry() {
        return industry;
    }

    public String getPosition() {
        return position;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getMobile() {
        return mobile;
    }

    public String getWorkPhone() {
        return workPhone;
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

    public UUID getImageFileId() {
        return imageFileId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getStatusId() {
        return statusId;
    }

    public UUID getRepresentativeUserId() {
        return representativeUserId;
    }

    public boolean isContractSigned() {
        return contractSigned;
    }

    public boolean isActive() {
        return active;
    }

    public Map<String, Object> getMetadata() {
        return Map.copyOf(metadata);
    }

    public Set<UUID> getTagIds() {
        return Set.copyOf(tagIds);
    }
}

package com.crmplatform.crm.contacts.application;

import com.crmplatform.crm.Contacts;
import com.crmplatform.crm.CrmReferences.ProjectUsage;
import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.crm.contacts.domain.Contact;
import com.crmplatform.crm.contacts.domain.ContactRepository;
import com.crmplatform.crm.locations.domain.Location;
import com.crmplatform.crm.locations.domain.LocationRepository;
import com.crmplatform.crm.refdata.application.RefDataService;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.events.AutomationChain;
import com.crmplatform.shared.events.DomainEvents.StatusChanged;
import com.crmplatform.shared.events.DomainEvents.TaggableDeleted;
import com.crmplatform.shared.events.DomainEvents.TaggableType;
import com.crmplatform.shared.events.DomainEvents.TagsChanged;
import com.crmplatform.shared.persistence.Address;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.shared.validation.PhoneNumbers;
import com.crmplatform.shared.web.Patch;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contacts (FR-CRM-04..09, BR-020..024) and the public {@link Contacts} API. */
@Service
public class ContactService implements Contacts {

    /** Default region for numbers entered without a country code (workspace locale default en-US). */
    static final String DEFAULT_PHONE_REGION = "US";
    private static final Pattern METADATA_KEY = Pattern.compile("^[a-zA-Z0-9_]{1,40}$");

    private final ContactRepository contacts;
    private final LocationRepository locations;
    private final RefDataService refData;
    private final LabelCatalog labels;
    private final WorkspaceMembers members;
    private final Files files;
    private final Geocoder geocoder;
    private final ObjectProvider<ProjectUsage> projectUsage;
    private final ApplicationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;

    public ContactService(ContactRepository contacts, LocationRepository locations, RefDataService refData,
            LabelCatalog labels, WorkspaceMembers members, Files files, Geocoder geocoder,
            ObjectProvider<ProjectUsage> projectUsage, ApplicationEventPublisher events, AuditTrail audit, Clock clock) {
        this.contacts = contacts;
        this.locations = locations;
        this.refData = refData;
        this.labels = labels;
        this.members = members;
        this.files = files;
        this.geocoder = geocoder;
        this.projectUsage = projectUsage;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Page<Contact> search(ContactFilter filter, Pageable pageable) {
        return contacts.findAll(ContactSpecs.matching(filter), pageable);
    }

    @Transactional(readOnly = true)
    public Contact get(UUID id) {
        return contacts.findById(id).orElseThrow(() -> ApiException.notFound("Contact"));
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public Result create(ContactData d) {
        String email = normalizeEmail(d.email());
        if (contacts.emailTaken(email, null)) {
            throw ApiException.conflict("EMAIL_TAKEN", "A contact with this email already exists");
        }
        Contact c = new Contact(parseType(d.type()), d.firstName(), d.lastName(), email);
        c.setName(d.title(), d.firstName(), d.middleName(), d.lastName());
        c.setOrganisation(d.company(), d.industry(), d.position());
        c.setPhones(phone(d.phone(), "phone"), phone(d.mobile(), "mobile"), phone(d.workPhone(), "workPhone"));
        c.setContractSigned(Boolean.TRUE.equals(d.contractSigned()));
        c.setActive(d.active() == null || d.active());
        c.setMetadata(validMetadata(d.metadata()));
        setCategory(c, d.categoryId());
        setStatus(c, d.statusId());
        setRepresentative(c, d.representativeUserId());
        Set<UUID> tags = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
        labels.requireTags(tags, "tagIds");
        c.replaceTags(tags);
        List<String> warnings = new ArrayList<>();
        applyAddress(c, d.address(), d.latitude(), d.longitude(), warnings);
        c.setImageFileId(d.imageFileId());
        Contact saved = contacts.save(c);
        files.attach(idList(d.imageFileId()), FileKind.IMAGE, "CONTACT", saved.getId(), "imageFileId");

        publishTagChanges(saved.getId(), Set.of(), tags);
        if (saved.getStatusId() != null) {
            publishStatusChange(saved.getId(), null, saved.getStatusId());
        }
        audit.created("CONTACT", saved.getId(), null, null, snapshot(saved));
        return new Result(saved, warnings);
    }

    @Transactional
    public Result update(UUID id, Patch<ContactData> patch) {
        Contact c = get(id);
        ContactData d = patch.value();
        Map<String, Object> before = snapshot(c);
        Set<UUID> previousTags = c.getTagIds();
        UUID previousStatus = c.getStatusId();
        UUID previousImage = c.getImageFileId();
        List<String> warnings = new ArrayList<>();

        patch.ifPresent("type", d.type(), v -> c.setType(parseType(required(v, "type"))));
        if (patch.has("name")) { // the name object is replaced as a whole
            c.setName(d.title(), required(d.firstName(), "name.first"), d.middleName(),
                    required(d.lastName(), "name.last"));
        }
        if (patch.has("company") || patch.has("industry") || patch.has("position")) {
            c.setOrganisation(patch.has("company") ? d.company() : c.getCompany(),
                    patch.has("industry") ? d.industry() : c.getIndustry(),
                    patch.has("position") ? d.position() : c.getPosition());
        }
        if (patch.has("email")) {
            String email = normalizeEmail(required(d.email(), "email"));
            if (contacts.emailTaken(email, id)) {
                throw ApiException.conflict("EMAIL_TAKEN", "A contact with this email already exists");
            }
            c.setEmail(email);
        }
        if (patch.has("phone") || patch.has("mobile") || patch.has("workPhone")) {
            c.setPhones(patch.has("phone") ? phone(d.phone(), "phone") : c.getPhone(),
                    patch.has("mobile") ? phone(d.mobile(), "mobile") : c.getMobile(),
                    patch.has("workPhone") ? phone(d.workPhone(), "workPhone") : c.getWorkPhone());
        }
        patch.ifPresent("contractSigned", d.contractSigned(), v -> c.setContractSigned(Boolean.TRUE.equals(v)));
        patch.ifPresent("active", d.active(), v -> c.setActive(v == null || v));
        patch.ifPresent("metadata", d.metadata(), v -> c.setMetadata(validMetadata(v)));
        patch.ifPresent("categoryId", d.categoryId(), v -> setCategory(c, v));
        patch.ifPresent("statusId", d.statusId(), v -> setStatus(c, v));
        patch.ifPresent("representativeUserId", d.representativeUserId(), v -> setRepresentative(c, v));
        if (patch.has("tagIds")) {
            Set<UUID> tags = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
            labels.requireTags(tags, "tagIds");
            c.replaceTags(tags);
        }
        if (patch.has("address") || patch.has("latitude") || patch.has("longitude")) {
            applyAddress(c, patch.has("address") ? d.address() : addressData(c.getAddress()),
                    patch.has("latitude") ? d.latitude() : null, patch.has("longitude") ? d.longitude() : null,
                    warnings);
        }
        if (patch.has("imageFileId")) {
            files.attach(idList(d.imageFileId()), FileKind.IMAGE, "CONTACT", id, "imageFileId");
            c.setImageFileId(d.imageFileId());
            if (previousImage != null && !previousImage.equals(d.imageFileId())) {
                files.delete(previousImage);
            }
        }

        Set<UUID> added = difference(c.getTagIds(), previousTags);
        Set<UUID> removed = difference(previousTags, c.getTagIds());
        publishTagChanges(id, removed, added);
        if (!Objects.equals(previousStatus, c.getStatusId())) {
            publishStatusChange(id, previousStatus, c.getStatusId());
        }
        audit.updated("CONTACT", id, null, null, before, snapshot(c));
        return new Result(c, warnings);
    }

    @Transactional
    public void delete(UUID id) {
        Contact c = get(id);
        ProjectUsage usage = projectUsage.getIfAvailable();
        if (usage != null && usage.contactHasProjects(id)) {
            throw ApiException.conflict("HAS_PROJECTS", "This contact still has active projects");
        }
        Instant now = Instant.now(clock);
        locations.findByContactId(id).forEach(l -> l.markDeleted(now, TenantContext.userId().orElse(null)));
        c.markDeleted(now, TenantContext.userId().orElse(null));
        if (c.getImageFileId() != null) {
            files.delete(c.getImageFileId());
        }
        events.publishEvent(new TaggableDeleted(TenantContext.requireWorkspaceId(), TaggableType.CONTACT, id));
        audit.deleted("CONTACT", id, null, null);
    }

    /** Deletes up to 100 contacts, reporting the outcome per id (EC-09). */
    @Transactional
    public BatchResult deleteMany(List<UUID> ids) {
        List<UUID> deleted = new ArrayList<>();
        List<UUID> notFound = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (UUID id : new java.util.LinkedHashSet<>(ids)) {
            if (contacts.findById(id).isEmpty()) {
                notFound.add(id);
                continue;
            }
            try {
                delete(id);
                deleted.add(id);
            } catch (ApiException e) {
                failed.add(Map.of("id", id, "code", e.code()));
            }
        }
        return new BatchResult(deleted, notFound, failed);
    }

    /** BR-024: deactivated members stop representing contacts. */
    @Transactional
    public void clearRepresentative(UUID userId) {
        contacts.findByRepresentativeUserId(userId).forEach(c -> c.setRepresentativeUserId(null));
    }

    /** EC-11: a deleted tag/status disappears from contacts. */
    @Transactional
    public void detachLabel(UUID labelId, boolean tag) {
        if (tag) {
            contacts.findByTag(labelId).forEach(c -> {
                Set<UUID> remaining = new HashSet<>(c.getTagIds());
                remaining.remove(labelId);
                c.replaceTags(remaining);
            });
        } else {
            contacts.findByStatusId(labelId).forEach(c -> c.setStatusId(null));
        }
    }

    // ------------------------------------------------------------------ Contacts (public API)

    @Override
    @Transactional(readOnly = true)
    public Optional<ContactSummary> find(UUID contactId) {
        return contactId == null ? Optional.empty() : contacts.findById(contactId).map(ContactService::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ContactSummary> findAll(Collection<UUID> contactIds) {
        if (contactIds == null || contactIds.isEmpty()) {
            return Map.of();
        }
        return contacts.findAllById(new HashSet<>(contactIds)).stream()
                .collect(Collectors.toMap(Contact::getId, ContactService::summary));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocationSummary> location(UUID locationId) {
        return locationId == null ? Optional.empty() : locations.findById(locationId).map(ContactService::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, LocationSummary> locations(Collection<UUID> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Map.of();
        }
        return locations.findAllById(new HashSet<>(locationIds)).stream()
                .collect(Collectors.toMap(Location::getId, ContactService::summary));
    }

    @Override
    @Transactional
    public void addTags(UUID contactId, Set<UUID> tagIds) {
        Contact c = get(contactId);
        labels.requireTags(tagIds, "tagIds");
        Set<UUID> next = new HashSet<>(c.getTagIds());
        next.addAll(tagIds);
        Set<UUID> added = difference(next, c.getTagIds());
        c.replaceTags(next);
        publishTagChanges(contactId, Set.of(), added);
    }

    @Override
    @Transactional
    public void removeTags(UUID contactId, Set<UUID> tagIds) {
        Contact c = get(contactId);
        Set<UUID> next = new HashSet<>(c.getTagIds());
        next.removeAll(tagIds);
        Set<UUID> removed = difference(c.getTagIds(), next);
        c.replaceTags(next);
        publishTagChanges(contactId, removed, Set.of());
    }

    @Override
    @Transactional
    public void setStatus(UUID contactId, UUID statusId) {
        Contact c = get(contactId);
        UUID previous = c.getStatusId();
        setStatus(c, statusId);
        if (!Objects.equals(previous, statusId)) {
            publishStatusChange(contactId, previous, statusId);
        }
    }

    @Override
    @Transactional
    public void clearStatus(UUID contactId, UUID statusId) {
        Contact c = get(contactId);
        UUID previous = c.getStatusId();
        if (previous != null && (statusId == null || statusId.equals(previous))) {
            c.setStatusId(null);
            publishStatusChange(contactId, previous, null);
        }
    }

    @Override
    @Transactional
    public boolean assignRepresentative(UUID contactId, UUID userId) {
        Contact c = get(contactId);
        if (c.getRepresentativeUserId() != null) {
            return false;
        }
        setRepresentative(c, userId);
        return true;
    }

    @Override
    @Transactional
    public void changeType(UUID contactId, String type, Boolean active, UUID statusId) {
        Contact c = get(contactId);
        c.setType(parseType(type));
        if (active != null) {
            c.setActive(active);
        }
        if (statusId != null) {
            setStatus(contactId, statusId);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void setCategory(Contact c, UUID categoryId) {
        if (categoryId != null && refData.categoriesById(List.of(categoryId)).isEmpty()) {
            throw ApiException.referenceNotFound("categoryId");
        }
        c.setCategoryId(categoryId);
    }

    private void setStatus(Contact c, UUID statusId) {
        labels.requireStatus(statusId, "statusId");
        c.setStatusId(statusId);
    }

    private void setRepresentative(Contact c, UUID userId) {
        if (userId != null && !members.isActiveMember(userId)) {
            throw ApiException.referenceNotFound("representativeUserId");
        }
        c.setRepresentativeUserId(userId);
    }

    private void applyAddress(Contact c, AddressData a, java.math.BigDecimal lat, java.math.BigDecimal lng,
            List<String> warnings) {
        Address address = a == null ? null : new Address(a.line1(), a.line2(), a.city(), a.region(), a.postalCode(),
                a.country());
        boolean changed = !Objects.equals(address, c.getAddress());
        c.setAddress(address);
        if (lat != null && lng != null) {
            c.setCoordinates(lat, lng);
        } else if (changed) {
            c.setCoordinates(null, null);
            if (address != null && !address.isEmpty()) {
                try {
                    geocoder.geocode(address).ifPresentOrElse(
                            coords -> c.setCoordinates(coords.latitude(), coords.longitude()),
                            () -> warnings.add("GEOCODING_FAILED"));
                } catch (RuntimeException e) {
                    warnings.add("GEOCODING_FAILED");
                }
            }
        }
    }

    private void publishTagChanges(UUID contactId, Set<UUID> removed, Set<UUID> added) {
        if (!added.isEmpty() || !removed.isEmpty()) {
            events.publishEvent(new TagsChanged(TenantContext.requireWorkspaceId(), TaggableType.CONTACT, contactId,
                    added, removed, AutomationChain.nextDepth()));
        }
    }

    private void publishStatusChange(UUID contactId, UUID previous, UUID next) {
        events.publishEvent(new StatusChanged(TenantContext.requireWorkspaceId(), TaggableType.CONTACT, contactId,
                previous, next, AutomationChain.nextDepth()));
    }

    private static Map<String, Object> validMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return Map.of();
        }
        if (metadata.size() > 50) {
            throw ApiException.badRequest("VALIDATION_ERROR", "metadata can have at most 50 keys");
        }
        for (Map.Entry<String, Object> e : metadata.entrySet()) {
            if (!METADATA_KEY.matcher(e.getKey()).matches()) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Invalid metadata key: " + e.getKey());
            }
            Object v = e.getValue();
            boolean scalar = v == null || v instanceof Number || v instanceof Boolean
                    || (v instanceof String s && s.length() <= 500);
            if (!scalar) {
                throw ApiException.badRequest("VALIDATION_ERROR", "metadata values must be short scalars");
            }
        }
        return metadata;
    }

    private static String phone(String raw, String field) {
        return PhoneNumbers.normalize(raw, DEFAULT_PHONE_REGION, field);
    }

    private static Contact.Type parseType(String type) {
        try {
            return Contact.Type.valueOf(type);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "type must be CLIENT, PROSPECT, PARTNER or LEAD");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw ApiException.badRequest("VALIDATION_ERROR", field + " is required");
        }
        return value;
    }

    private static Set<UUID> difference(Set<UUID> a, Set<UUID> b) {
        Set<UUID> result = new HashSet<>(a);
        result.removeAll(b);
        return result;
    }

    private static List<UUID> idList(UUID id) {
        return id == null ? List.of() : List.of(id);
    }

    private static AddressData addressData(Address a) {
        return a == null ? null : new AddressData(a.getLine1(), a.getLine2(), a.getCity(), a.getRegion(),
                a.getPostalCode(), a.getCountry());
    }

    static Map<String, Object> snapshot(Contact c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", c.getType().name());
        m.put("name", c.fullName());
        m.put("email", c.getEmail());
        m.put("company", c.getCompany());
        m.put("phone", c.getPhone());
        m.put("statusId", c.getStatusId());
        m.put("categoryId", c.getCategoryId());
        m.put("representativeUserId", c.getRepresentativeUserId());
        m.put("tagIds", c.getTagIds().stream().map(UUID::toString).sorted().toList());
        m.put("active", c.isActive());
        return m;
    }

    private static ContactSummary summary(Contact c) {
        return new ContactSummary(c.getId(), c.getType().name(), c.fullName(), c.getCompany(), c.getEmail(),
                c.getPhone(), c.getRepresentativeUserId(), c.getStatusId(), c.getMetadata(),
                c.getAddress() == null ? null : c.getAddress().getCity());
    }

    private static LocationSummary summary(Location l) {
        return new LocationSummary(l.getId(), l.getContactId(), l.getName(), l.getType().name(),
                l.getAddress() == null ? null : l.getAddress().getCity());
    }

    // ------------------------------------------------------------------ types

    public record AddressData(String line1, String line2, String city, String region, String postalCode,
            String country) {
    }

    public record ContactData(String type, String title, String firstName, String middleName, String lastName,
            String company, String industry, String position, String email, String phone, String mobile,
            String workPhone, AddressData address, java.math.BigDecimal latitude, java.math.BigDecimal longitude,
            UUID imageFileId, UUID categoryId, UUID statusId, Set<UUID> tagIds, UUID representativeUserId,
            Boolean contractSigned, Boolean active, Map<String, Object> metadata) {
    }

    public record ContactFilter(String type, UUID statusId, UUID categoryId, UUID tagId, UUID representativeId,
            Boolean active, String q) {
    }

    public record Result(Contact contact, List<String> warnings) {
    }

    public record BatchResult(List<UUID> deleted, List<UUID> notFound, List<Map<String, Object>> failed) {
    }

}

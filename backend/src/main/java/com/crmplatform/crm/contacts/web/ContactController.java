package com.crmplatform.crm.contacts.web;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.crm.LabelCatalog.LabelSummary;
import com.crmplatform.crm.contacts.application.ContactService;
import com.crmplatform.crm.contacts.application.ContactService.AddressData;
import com.crmplatform.crm.contacts.application.ContactService.BatchResult;
import com.crmplatform.crm.contacts.application.ContactService.ContactData;
import com.crmplatform.crm.contacts.application.ContactService.ContactFilter;
import com.crmplatform.crm.contacts.domain.Contact;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.identity.WorkspaceMembers.MemberSummary;
import com.crmplatform.shared.persistence.Address;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patches;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Contacts (FR-CRM-04..08). */
@RestController
@RequestMapping("/api/v1/contacts")
@Tag(name = "Contacts")
public class ContactController {

    private static final Map<String, String> SORT = Map.of("createdAt", "createdAt", "lastName", "lastName",
            "firstName", "firstName", "company", "company", "email", "email", "updatedAt", "updatedAt");

    private final ContactService contacts;
    private final LabelCatalog labels;
    private final WorkspaceMembers members;
    private final Patches patches;

    public ContactController(ContactService contacts, LabelCatalog labels, WorkspaceMembers members, Patches patches) {
        this.contacts = contacts;
        this.labels = labels;
        this.members = members;
        this.patches = patches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:read')")
    public PageResponse<ContactResponse> list(@RequestParam(required = false) String type,
            @RequestParam(required = false) UUID statusId, @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID tagId, @RequestParam(required = false) UUID representativeId,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) String q,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        if (type != null && !List.of("CLIENT", "PROSPECT", "PARTNER", "LEAD").contains(type)) {
            throw com.crmplatform.shared.error.ApiException.badRequest("VALIDATION_ERROR", "Unknown contact type");
        }
        if (kind != null && !List.of("INDIVIDUAL", "COMPANY").contains(kind)) {
            throw com.crmplatform.shared.error.ApiException.badRequest("VALIDATION_ERROR", "kind must be INDIVIDUAL or COMPANY");
        }
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Contact> result = contacts.search(new ContactFilter(type, statusId, categoryId, tagId, representativeId,
                active, q, kind), pageable);
        return PageResponse.of(result.map(new Assembler(result.getContent())::toResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:read')")
    public ContactResponse get(@PathVariable UUID id) {
        Contact c = contacts.get(id);
        return new Assembler(List.of(c)).toResponse(c);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:write')")
    public ResponseEntity<ContactResponse> create(@Valid @RequestBody ContactRequest body) {
        ContactService.Result r = contacts.create(body.toData());
        ContactResponse response = new Assembler(List.of(r.contact())).toResponse(r.contact()).withWarnings(r.warnings());
        return ResponseEntity.created(URI.create("/api/v1/contacts/" + r.contact().getId())).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:write')")
    public ContactResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        var patch = patches.read(body, ContactRequest.class);
        ContactService.Result r = contacts.update(id, new com.crmplatform.shared.web.Patch<>(patch.value().toData(),
                patch.fields()));
        return new Assembler(List.of(r.contact())).toResponse(r.contact()).withWarnings(r.warnings());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        contacts.delete(id);
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('crm:delete')")
    public BatchResult deleteMany(@Valid @RequestBody BatchDeleteRequest body) {
        return contacts.deleteMany(body.ids());
    }

    /** Resolves tag/status/representative display data for a page of contacts in bulk. */
    private final class Assembler {

        private final Map<UUID, LabelSummary> tagSummaries;
        private final Map<UUID, MemberSummary> people;

        Assembler(Collection<Contact> page) {
            Set<UUID> tagIds = new HashSet<>();
            Set<UUID> userIds = new HashSet<>();
            page.forEach(c -> {
                tagIds.addAll(c.getTagIds());
                if (c.getRepresentativeUserId() != null) {
                    userIds.add(c.getRepresentativeUserId());
                }
            });
            this.tagSummaries = labels.tags(tagIds);
            this.people = members.findAll(userIds);
        }

        ContactResponse toResponse(Contact c) {
            List<LabelSummary> tags = new ArrayList<>();
            c.getTagIds().forEach(id -> {
                LabelSummary t = tagSummaries.get(id);
                if (t != null) {
                    tags.add(t);
                }
            });
            tags.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            MemberSummary rep = c.getRepresentativeUserId() == null ? null : people.get(c.getRepresentativeUserId());
            Address a = c.getAddress();
            return new ContactResponse(c.getId(), c.getType().name(), c.getKind().name(), new Name(c.getTitle(), c.getFirstName(),
                    c.getMiddleName(), c.getLastName()), c.fullName(), c.getCompany(), c.getIndustry(),
                    c.getPosition(), c.getEmail(), c.getPhone(), c.getMobile(), c.getWorkPhone(),
                    a == null ? null : new AddressData(a.getLine1(), a.getLine2(), a.getCity(), a.getRegion(),
                            a.getPostalCode(), a.getCountry()),
                    c.getLatitude(), c.getLongitude(), c.getImageFileId(), c.getCategoryId(),
                    labels.status(c.getStatusId()).orElse(null), tags,
                    rep == null ? null : new Person(rep.userId(), rep.fullName(), rep.email()),
                    c.isContractSigned(), c.isActive(), c.getMetadata(), c.getCreatedAt(), c.getUpdatedAt(),
                    c.getVersion(), List.of());
        }
    }

    // ------------------------------------------------------------------ DTOs

    public record NameRequest(@Pattern(regexp = "^(Mr|Mrs|Ms|Mx|Dr|Eng|Prof)$") String title,
            @Size(max = 60) String first, @Size(max = 60) String middle,
            @Size(max = 60) String last) {
    }

    public record AddressRequest(@Size(max = 200) String line1, @Size(max = 200) String line2,
            @Size(max = 100) String city, @Size(max = 100) String region, @Size(max = 20) String postalCode,
            @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166 alpha-2 code") String country) {
        AddressData toData() {
            return new AddressData(line1, line2, city, region, postalCode, country);
        }
    }

    public record ContactRequest(
            @NotBlank @Pattern(regexp = "^(CLIENT|PROSPECT|PARTNER|LEAD)$") String type,
            @Valid NameRequest name,
            @Size(max = 150) String company,
            @Size(max = 100) String industry,
            @Size(max = 100) String position,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @Size(max = 30) String mobile,
            @Size(max = 30) String workPhone,
            @Valid AddressRequest address,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            UUID imageFileId,
            UUID categoryId,
            UUID statusId,
            @Size(max = 20) Set<UUID> tagIds,
            UUID representativeUserId,
            Boolean contractSigned,
            Boolean active,
            Map<String, Object> metadata,
            @Pattern(regexp = "^(INDIVIDUAL|COMPANY)$") String kind) {

        ContactData toData() {
            NameRequest n = name;
            return new ContactData(type, n == null ? null : n.title(), n == null ? null : n.first(),
                    n == null ? null : n.middle(), n == null ? null : n.last(), company, industry, position, email,
                    phone, mobile, workPhone, address == null ? null : address.toData(), latitude, longitude,
                    imageFileId, categoryId, statusId, tagIds, representativeUserId, contractSigned, active, metadata, kind);
        }
    }

    public record BatchDeleteRequest(@NotEmpty @Size(max = 100) List<UUID> ids) {
    }

    public record Name(String title, String first, String middle, String last) {
    }

    public record Person(UUID userId, String name, String email) {
    }

    public record ContactResponse(UUID id, String type, String kind, Name name, String fullName, String company, String industry,
            String position, String email, String phone, String mobile, String workPhone, AddressData address,
            BigDecimal latitude, BigDecimal longitude, UUID imageFileId, UUID categoryId, LabelSummary status,
            List<LabelSummary> tags, Person representative, boolean contractSigned, boolean active,
            Map<String, Object> metadata, Instant createdAt, Instant updatedAt, long version, List<String> warnings) {

        ContactResponse withWarnings(List<String> w) {
            return new ContactResponse(id, type, kind, name, fullName, company, industry, position, email, phone, mobile,
                    workPhone, address, latitude, longitude, imageFileId, categoryId, status, tags, representative,
                    contractSigned, active, metadata, createdAt, updatedAt, version, w);
        }
    }
}

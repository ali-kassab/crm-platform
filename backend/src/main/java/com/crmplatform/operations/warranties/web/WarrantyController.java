package com.crmplatform.operations.warranties.web;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.crm.LabelCatalog.LabelSummary;
import com.crmplatform.files.Files.FileSummary;
import com.crmplatform.operations.warranties.application.WarrantyService;
import com.crmplatform.operations.warranties.application.WarrantyService.WarrantyData;
import com.crmplatform.operations.warranties.application.WarrantyService.WarrantyFilter;
import com.crmplatform.operations.warranties.domain.Warranty;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.shared.web.Patches;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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

/** Warranties (FR-OPS-06). */
@RestController
@RequestMapping("/api/v1/warranties")
@Tag(name = "Warranties")
public class WarrantyController {

    private static final String STATUS = "^(ACTIVE|EXPIRED|CANCELLED)$";
    private static final Map<String, String> SORT = Map.of("createdAt", "createdAt", "updatedAt", "updatedAt",
            "title", "title", "provider", "provider", "startDate", "startDate", "endDate", "endDate");

    private final WarrantyService warranties;
    private final LabelCatalog labels;
    private final Patches patches;

    public WarrantyController(WarrantyService warranties, LabelCatalog labels, Patches patches) {
        this.warranties = warranties;
        this.labels = labels;
        this.patches = patches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ops:read')")
    public PageResponse<WarrantyResponse> list(@RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID jobId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endingBefore,
            @RequestParam(required = false) UUID contactId, @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        if (status != null && !status.matches(STATUS)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown warranty status");
        }
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.ASC, "endDate"));
        Page<Warranty> result = warranties.search(new WarrantyFilter(projectId, jobId, status, provider, endingBefore,
                q, contactId, equipmentId), pageable);
        Map<UUID, LabelSummary> tags = tagsOf(result.getContent());
        return PageResponse.of(result.map(w -> WarrantyResponse.of(w, tags, null)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:read')")
    public WarrantyResponse get(@PathVariable UUID id) {
        return detail(warranties.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ops:write')")
    public ResponseEntity<WarrantyResponse> create(@Valid @RequestBody WarrantyRequest body) {
        Warranty w = warranties.create(body.toData());
        return ResponseEntity.created(URI.create("/api/v1/warranties/" + w.getId())).body(detail(w));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:write')")
    public WarrantyResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        var patch = patches.read(body, WarrantyRequest.class);
        return detail(warranties.update(id, new Patch<>(patch.value().toData(), patch.fields())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        warranties.delete(id);
    }

    private WarrantyResponse detail(Warranty w) {
        return WarrantyResponse.of(w, tagsOf(List.of(w)), warranties.documentsOf(w.getId()));
    }

    private Map<UUID, LabelSummary> tagsOf(Collection<Warranty> list) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(w -> ids.addAll(w.getTagIds()));
        return labels.tags(ids);
    }

    // ------------------------------------------------------------------ DTOs

    public record WarrantyRequest(
            @NotBlank @Size(min = 3, max = 100) String title,
            @NotBlank @Size(max = 150) String provider,
            @Pattern(regexp = STATUS) String status,
            @Size(max = 2000) String note,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            UUID projectId,
            UUID jobId,
            @Size(max = 20) Set<UUID> tagIds,
            @Size(max = 20) List<UUID> documentFileIds,
            UUID contactId,
            UUID locationId,
            UUID equipmentId,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(365) Integer reminderDays) {

        WarrantyData toData() {
            return new WarrantyData(title, provider, status, note, startDate, endDate, projectId, jobId, tagIds,
                    documentFileIds, contactId, locationId, equipmentId, reminderDays);
        }
    }

    public record DocumentRef(UUID id, String name, String contentType, long sizeBytes) {
    }

    public record WarrantyResponse(UUID id, String title, String provider, String status, String note,
            LocalDate startDate, LocalDate endDate, UUID projectId, UUID jobId, UUID contactId, UUID locationId,
            UUID equipmentId, int reminderDays, Instant reminderSentAt, List<LabelSummary> tags,
            List<DocumentRef> documents, Instant createdAt, Instant updatedAt, long version) {

        static WarrantyResponse of(Warranty w, Map<UUID, LabelSummary> tagMap, List<FileSummary> docs) {
            List<LabelSummary> tags = new ArrayList<>(w.getTagIds().stream().map(tagMap::get)
                    .filter(Objects::nonNull).toList());
            tags.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            return new WarrantyResponse(w.getId(), w.getTitle(), w.getProvider(), w.getStatus().name(), w.getNote(),
                    w.getStartDate(), w.getEndDate(), w.getProjectId(), w.getJobId(), w.getContactId(), w.getLocationId(),
                    w.getEquipmentId(), w.getReminderDays(), w.getReminderSentAt(), tags,
                    docs == null ? null : docs.stream()
                            .map(f -> new DocumentRef(f.id(), f.name(), f.contentType(), f.sizeBytes())).toList(),
                    w.getCreatedAt(), w.getUpdatedAt(), w.getVersion());
        }
    }
}

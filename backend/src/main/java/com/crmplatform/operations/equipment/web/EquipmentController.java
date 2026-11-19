package com.crmplatform.operations.equipment.web;

import com.crmplatform.operations.equipment.application.EquipmentService;
import com.crmplatform.operations.equipment.application.EquipmentService.EquipmentData;
import com.crmplatform.operations.equipment.application.EquipmentService.EquipmentFilter;
import com.crmplatform.operations.equipment.domain.Equipment;
import com.crmplatform.operations.jobs.domain.Job;
import com.crmplatform.operations.warranties.application.WarrantyService;
import com.crmplatform.operations.warranties.domain.Warranty;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.shared.web.Patches;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Equipment register (R6) and the devices a work order dealt with. */
@RestController
@Tag(name = "Equipment")
public class EquipmentController {

    private static final Map<String, String> SORT = Map.of("createdAt", "createdAt", "name", "name",
            "installedOn", "installedOn", "category", "category");

    private final EquipmentService equipment;
    private final WarrantyService warranties;
    private final Patches patches;

    public EquipmentController(EquipmentService equipment, WarrantyService warranties, Patches patches) {
        this.equipment = equipment;
        this.warranties = warranties;
        this.patches = patches;
    }

    @GetMapping("/api/v1/equipment")
    @PreAuthorize("hasAuthority('ops:read')")
    public PageResponse<EquipmentResponse> list(@RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID locationId, @RequestParam(required = false) String category,
            @RequestParam(required = false) String status, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.ASC, "name"));
        return PageResponse.of(equipment.search(new EquipmentFilter(contactId, locationId, category, status, q),
                pageable), e -> EquipmentResponse.of(e, null, null));
    }

    @GetMapping("/api/v1/equipment/{id}")
    @PreAuthorize("hasAuthority('ops:read')")
    public EquipmentResponse get(@PathVariable UUID id) {
        return detail(equipment.get(id));
    }

    @PostMapping("/api/v1/equipment")
    @PreAuthorize("hasAuthority('ops:write')")
    public ResponseEntity<EquipmentResponse> create(@Valid @RequestBody EquipmentRequest body) {
        Equipment e = equipment.create(body.toData());
        return ResponseEntity.created(URI.create("/api/v1/equipment/" + e.getId())).body(detail(e));
    }

    @PatchMapping("/api/v1/equipment/{id}")
    @PreAuthorize("hasAuthority('ops:write')")
    public EquipmentResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        var patch = patches.read(body, EquipmentRequest.class);
        return detail(equipment.update(id, new Patch<>(patch.value().toData(), patch.fields())));
    }

    @DeleteMapping("/api/v1/equipment/{id}")
    @PreAuthorize("hasAuthority('ops:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        equipment.delete(id);
    }

    @GetMapping("/api/v1/jobs/{jobId}/equipment")
    @PreAuthorize("hasAnyAuthority('ops:read', 'ops:assigned')")
    public List<EquipmentResponse> ofJob(@PathVariable UUID jobId) {
        return equipment.ofJob(jobId).stream().map(e -> EquipmentResponse.of(e, null, null)).toList();
    }

    @PutMapping("/api/v1/jobs/{jobId}/equipment")
    @PreAuthorize("hasAnyAuthority('ops:write', 'ops:assigned')")
    public List<EquipmentResponse> setForJob(@PathVariable UUID jobId, @Valid @RequestBody JobEquipmentRequest body) {
        return equipment.setJobEquipment(jobId, body.equipmentIds()).stream()
                .map(e -> EquipmentResponse.of(e, null, null)).toList();
    }

    private EquipmentResponse detail(Equipment e) {
        return EquipmentResponse.of(e, equipment.serviceHistory(e.getId()), warranties.ofEquipment(e.getId()));
    }

    // ------------------------------------------------------------------ DTOs

    public record EquipmentRequest(
            @NotNull UUID contactId,
            @NotNull UUID locationId,
            @NotBlank String category,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 100) String manufacturer,
            @Size(max = 100) String model,
            @Size(max = 100) String serialNumber,
            LocalDate installedOn,
            UUID installedJobId,
            String status,
            @Size(max = 2000) String notes) {

        EquipmentData toData() {
            return new EquipmentData(contactId, locationId, category, name, manufacturer, model, serialNumber,
                    installedOn, installedJobId, status, notes);
        }
    }

    public record JobEquipmentRequest(@NotNull @Size(max = 100) Set<UUID> equipmentIds) {
    }

    public record ServiceEntry(UUID jobId, String title, String type, String status, LocalDate startDate,
            Instant completedAt) {
        static ServiceEntry of(Job j) {
            return new ServiceEntry(j.getId(), j.getTitle(), j.getType().name(), j.getStatus().name(),
                    j.getStartDate(), j.getCompletedAt());
        }
    }

    public record WarrantyRef(UUID id, String title, String provider, String status, LocalDate endDate) {
        static WarrantyRef of(Warranty w) {
            return new WarrantyRef(w.getId(), w.getTitle(), w.getProvider(), w.getStatus().name(), w.getEndDate());
        }
    }

    public record EquipmentResponse(UUID id, UUID contactId, UUID locationId, String category, String name,
            String manufacturer, String model, String serialNumber, LocalDate installedOn, UUID installedJobId,
            String status, String notes, List<ServiceEntry> serviceHistory, List<WarrantyRef> warranties,
            Instant createdAt, Instant updatedAt, long version) {

        static EquipmentResponse of(Equipment e, List<Job> history, List<Warranty> warranties) {
            return new EquipmentResponse(e.getId(), e.getContactId(), e.getLocationId(), e.getCategory().name(),
                    e.getName(), e.getManufacturer(), e.getModel(), e.getSerialNumber(), e.getInstalledOn(),
                    e.getInstalledJobId(), e.getStatus().name(), e.getNotes(),
                    history == null ? null : history.stream().map(ServiceEntry::of).toList(),
                    warranties == null ? null : warranties.stream().map(WarrantyRef::of).toList(),
                    e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
        }
    }
}

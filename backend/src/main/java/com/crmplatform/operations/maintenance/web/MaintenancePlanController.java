package com.crmplatform.operations.maintenance.web;

import com.crmplatform.operations.maintenance.application.MaintenanceService;
import com.crmplatform.operations.maintenance.application.MaintenanceService.OccurrenceView;
import com.crmplatform.operations.maintenance.application.MaintenanceService.PlanData;
import com.crmplatform.operations.maintenance.application.MaintenanceService.PlanFilter;
import com.crmplatform.operations.maintenance.domain.MaintenancePlan;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.shared.web.Patches;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Preventive-maintenance plans / agreements (R6). */
@RestController
@RequestMapping("/api/v1/maintenance-plans")
@Tag(name = "Maintenance plans")
public class MaintenancePlanController {

    private static final Map<String, String> SORT = Map.of("createdAt", "createdAt", "title", "title",
            "nextDueDate", "nextDueDate", "endDate", "endDate");

    private final MaintenanceService maintenance;
    private final Patches patches;

    public MaintenancePlanController(MaintenanceService maintenance, Patches patches) {
        this.maintenance = maintenance;
        this.patches = patches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ops:read')")
    public PageResponse<PlanResponse> list(@RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID locationId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.ASC, "nextDueDate"));
        return PageResponse.of(maintenance.search(new PlanFilter(contactId, locationId, status, q), pageable),
                p -> PlanResponse.of(p, null));
    }

    /** Preventive jobs past their due date that are still open. */
    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('ops:read')")
    public List<OccurrenceView> overdue() {
        return maintenance.overdue();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:read')")
    public PlanResponse get(@PathVariable UUID id) {
        return PlanResponse.of(maintenance.get(id), maintenance.occurrencesOf(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ops:write')")
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanRequest body) {
        MaintenancePlan p = maintenance.create(body.toData());
        return ResponseEntity.created(URI.create("/api/v1/maintenance-plans/" + p.getId()))
                .body(PlanResponse.of(p, List.of()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:write')")
    public PlanResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        var patch = patches.read(body, PlanRequest.class);
        MaintenancePlan p = maintenance.update(id, new Patch<>(patch.value().toData(), patch.fields()));
        return PlanResponse.of(p, maintenance.occurrencesOf(id));
    }

    /** Generates whatever is due now (the daily run does the same). */
    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAuthority('ops:write')")
    public Map<String, Integer> generate(@PathVariable UUID id) {
        maintenance.get(id);
        return Map.of("generated", maintenance.generateDue(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ops:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        maintenance.delete(id);
    }

    public record PlanRequest(
            @NotNull UUID contactId,
            @NotNull UUID locationId,
            @NotBlank @Size(max = 150) String title,
            @Size(max = 2000) String description,
            @NotBlank String frequency,
            @Min(1) @Max(730) Integer intervalDays,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Min(0) @Max(90) Integer leadDays,
            @Min(1) @Max(720) Integer responseHours,
            @Min(1) @Max(1000) Integer includedVisits,
            @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal visitBudget,
            String status,
            @Size(max = 20) Set<UUID> assigneeUserIds,
            @Size(max = 100) Set<UUID> equipmentIds) {

        PlanData toData() {
            return new PlanData(contactId, locationId, title, description, frequency, intervalDays, startDate, endDate,
                    leadDays, responseHours, includedVisits, visitBudget, status, assigneeUserIds, equipmentIds);
        }
    }

    public record PlanResponse(UUID id, UUID contactId, UUID locationId, String title, String description,
            String frequency, Integer intervalDays, LocalDate startDate, LocalDate endDate, LocalDate nextDueDate,
            int leadDays, Integer responseHours, Integer includedVisits, int generatedCount, String visitBudget,
            String status, Set<UUID> assigneeUserIds, Set<UUID> equipmentIds, List<OccurrenceView> occurrences,
            Instant createdAt, Instant updatedAt, long version) {

        static PlanResponse of(MaintenancePlan p, List<OccurrenceView> occurrences) {
            return new PlanResponse(p.getId(), p.getContactId(), p.getLocationId(), p.getTitle(), p.getDescription(),
                    p.getFrequency().name(), p.getIntervalDays(), p.getStartDate(), p.getEndDate(), p.getNextDueDate(),
                    p.getLeadDays(), p.getResponseHours(), p.getIncludedVisits(), p.getGeneratedCount(),
                    p.getVisitBudget().setScale(2, RoundingMode.HALF_UP).toPlainString(), p.getStatus().name(),
                    p.getAssigneeUserIds(), p.getEquipmentIds(), occurrences, p.getCreatedAt(), p.getUpdatedAt(),
                    p.getVersion());
        }
    }
}

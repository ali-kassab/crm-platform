package com.crmplatform.crm.locations.web;

import com.crmplatform.crm.contacts.application.ContactService.AddressData;
import com.crmplatform.crm.contacts.web.ContactController.AddressRequest;
import com.crmplatform.crm.locations.application.LocationService;
import com.crmplatform.crm.locations.application.LocationService.LocationData;
import com.crmplatform.crm.locations.domain.Location;
import com.crmplatform.crm.locations.domain.Location.DayHours;
import com.crmplatform.shared.persistence.Address;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.shared.web.Patches;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "Locations")
public class LocationController {

    private static final Map<String, String> SORT = Map.of("name", "name", "createdAt", "createdAt");

    private final LocationService locations;
    private final Patches patches;

    public LocationController(LocationService locations, Patches patches) {
        this.locations = locations;
        this.patches = patches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:read')")
    public PageResponse<LocationResponse> list(@RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) String type, @RequestParam(required = false) String city,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by("name"));
        return PageResponse.of(locations.search(contactId, type, city, Pagination.likePattern(q), pageable),
                l -> LocationResponse.of(l, List.of()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:read')")
    public LocationResponse get(@PathVariable UUID id) {
        return LocationResponse.of(locations.get(id), List.of());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:write')")
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest body) {
        var r = locations.create(body.toData());
        return ResponseEntity.created(URI.create("/api/v1/locations/" + r.location().getId()))
                .body(LocationResponse.of(r.location(), r.warnings()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:write')")
    public LocationResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        Patch<LocationRequest> patch = patches.read(body, LocationRequest.class);
        var r = locations.update(id, new Patch<>(patch.value().toData(), patch.fields()));
        return LocationResponse.of(r.location(), r.warnings());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        locations.delete(id);
    }

    public record LocationRequest(
            @NotNull UUID contactId,
            @NotBlank @Pattern(regexp = "^(RESIDENTIAL|COMMERCIAL|INDUSTRIAL|RETAIL|WAREHOUSE|OFFICE)$") String type,
            @NotBlank @Size(max = 150) String name,
            @Valid AddressRequest address,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 2000) String accessInstructions,
            Boolean parkingAvailable,
            Map<String, DayHours> workingHours) {
        LocationData toData() {
            return new LocationData(contactId, type, name, address == null ? null : new AddressData(address.line1(),
                    address.line2(), address.city(), address.region(), address.postalCode(), address.country()),
                    latitude, longitude, accessInstructions, parkingAvailable, workingHours);
        }
    }

    public record LocationResponse(UUID id, UUID contactId, String type, String name, AddressData address,
            BigDecimal latitude, BigDecimal longitude, String accessInstructions, boolean parkingAvailable,
            Map<String, DayHours> workingHours, Instant createdAt, long version, List<String> warnings) {
        static LocationResponse of(Location l, List<String> warnings) {
            Address a = l.getAddress();
            return new LocationResponse(l.getId(), l.getContactId(), l.getType().name(), l.getName(),
                    a == null ? null : new AddressData(a.getLine1(), a.getLine2(), a.getCity(), a.getRegion(),
                            a.getPostalCode(), a.getCountry()),
                    l.getLatitude(), l.getLongitude(), l.getAccessInstructions(), l.isParkingAvailable(),
                    l.getWorkingHours(), l.getCreatedAt(), l.getVersion(), warnings);
        }
    }
}

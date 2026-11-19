package com.crmplatform.crm.locations.application;

import com.crmplatform.crm.CrmReferences.ProjectUsage;
import com.crmplatform.crm.contacts.application.ContactService.AddressData;
import com.crmplatform.crm.contacts.application.Geocoder;
import com.crmplatform.crm.contacts.domain.ContactRepository;
import com.crmplatform.crm.locations.domain.Location;
import com.crmplatform.crm.locations.domain.Location.DayHours;
import com.crmplatform.crm.locations.domain.LocationRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.persistence.Address;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.shared.web.Patch;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer sites (FR-CRM-10, BR-025..028). */
@Service
public class LocationService {

    private final LocationRepository locations;
    private final ContactRepository contacts;
    private final Geocoder geocoder;
    private final ObjectProvider<ProjectUsage> projectUsage;
    private final AuditTrail audit;
    private final Clock clock;

    public LocationService(LocationRepository locations, ContactRepository contacts, Geocoder geocoder,
            ObjectProvider<ProjectUsage> projectUsage, AuditTrail audit, Clock clock) {
        this.locations = locations;
        this.contacts = contacts;
        this.geocoder = geocoder;
        this.projectUsage = projectUsage;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Location> search(UUID contactId, String type, String city, String q, Pageable pageable) {
        return locations.search(contactId, type == null ? null : parseType(type),
                city == null || city.isBlank() ? null : city.trim().toLowerCase(), q, pageable);
    }

    @Transactional(readOnly = true)
    public Location get(UUID id) {
        return locations.findById(id).orElseThrow(() -> ApiException.notFound("Location"));
    }

    @Transactional
    public Result create(LocationData d) {
        requireContact(d.contactId());
        Location l = new Location(d.contactId(), parseType(d.type()), d.name());
        l.setAccessInstructions(d.accessInstructions());
        l.setParkingAvailable(Boolean.TRUE.equals(d.parkingAvailable()));
        if (d.workingHours() != null) {
            l.setWorkingHours(validHours(d.workingHours()));
        }
        List<String> warnings = new ArrayList<>();
        applyAddress(l, d.address(), d.latitude(), d.longitude(), warnings);
        Location saved = locations.save(l);
        audit.created("LOCATION", saved.getId(), null, null, Map.of("name", saved.getName(),
                "contactId", saved.getContactId()));
        return new Result(saved, warnings);
    }

    @Transactional
    public Result update(UUID id, Patch<LocationData> patch) {
        Location l = get(id);
        LocationData d = patch.value();
        Map<String, Object> before = snapshot(l);
        if (patch.has("contactId")) {
            requireContact(d.contactId());
            l.setContactId(d.contactId());
        }
        patch.ifPresent("type", d.type(), v -> l.setType(parseType(v)));
        patch.ifPresent("name", d.name(), v -> {
            if (v == null || v.isBlank()) {
                throw ApiException.badRequest("VALIDATION_ERROR", "name is required");
            }
            l.setName(v);
        });
        patch.ifPresent("accessInstructions", d.accessInstructions(), l::setAccessInstructions);
        patch.ifPresent("parkingAvailable", d.parkingAvailable(), v -> l.setParkingAvailable(Boolean.TRUE.equals(v)));
        patch.ifPresent("workingHours", d.workingHours(),
                v -> l.setWorkingHours(v == null ? Location.defaultHours() : validHours(v)));
        List<String> warnings = new ArrayList<>();
        if (patch.has("address") || patch.has("latitude") || patch.has("longitude")) {
            AddressData address = patch.has("address") ? d.address() : toData(l.getAddress());
            applyAddress(l, address, patch.has("latitude") ? d.latitude() : null,
                    patch.has("longitude") ? d.longitude() : null, warnings);
        }
        audit.updated("LOCATION", id, null, null, before, snapshot(l));
        return new Result(l, warnings);
    }

    @Transactional
    public void delete(UUID id) {
        Location l = get(id);
        // every module that references sites (projects, jobs) is asked
        if (projectUsage.stream().anyMatch(u -> u.locationHasProjects(id))) {
            throw ApiException.conflict("HAS_PROJECTS", "This location still has active projects or jobs");
        }
        l.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        audit.deleted("LOCATION", id, null, null);
    }

    private void requireContact(UUID contactId) {
        if (contactId == null || !contacts.existsById(contactId)) {
            throw ApiException.referenceNotFound("contactId");
        }
    }

    private void applyAddress(Location l, AddressData a, BigDecimal lat, BigDecimal lng, List<String> warnings) {
        Address address = a == null ? null : new Address(a.line1(), a.line2(), a.city(), a.region(), a.postalCode(),
                a.country());
        boolean changed = !Objects.equals(address, l.getAddress());
        l.setAddress(address);
        if (lat != null && lng != null) {
            l.setCoordinates(lat, lng);
        } else if (changed) {
            l.setCoordinates(null, null);
            if (address != null && !address.isEmpty()) {
                try {
                    geocoder.geocode(address).ifPresentOrElse(c -> l.setCoordinates(c.latitude(), c.longitude()),
                            () -> warnings.add("GEOCODING_FAILED"));
                } catch (RuntimeException e) {
                    warnings.add("GEOCODING_FAILED");
                }
            }
        }
    }

    /** Seven weekday keys, HH:mm times, close after open unless closed (VALIDATION §3). */
    static Map<String, DayHours> validHours(Map<String, DayHours> hours) {
        Map<String, DayHours> result = new LinkedHashMap<>();
        for (String day : Location.WEEKDAYS) {
            DayHours h = hours.get(day);
            if (h == null) {
                throw ApiException.badRequest("VALIDATION_ERROR", "workingHours must include " + day);
            }
            try {
                LocalTime open = LocalTime.parse(h.open());
                LocalTime close = LocalTime.parse(h.close());
                if (!h.closed() && !close.isAfter(open)) {
                    throw ApiException.badRequest("VALIDATION_ERROR", "workingHours." + day + " closes before it opens");
                }
            } catch (DateTimeParseException | NullPointerException e) {
                throw ApiException.badRequest("VALIDATION_ERROR", "workingHours." + day + " must use HH:mm");
            }
            result.put(day, h);
        }
        if (hours.size() != Location.WEEKDAYS.size()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "workingHours has unknown days");
        }
        return result;
    }

    private static Location.Type parseType(String type) {
        try {
            return Location.Type.valueOf(type);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "type must be RESIDENTIAL, COMMERCIAL, INDUSTRIAL, RETAIL, WAREHOUSE or OFFICE");
        }
    }

    private static AddressData toData(Address a) {
        return a == null ? null : new AddressData(a.getLine1(), a.getLine2(), a.getCity(), a.getRegion(),
                a.getPostalCode(), a.getCountry());
    }

    private static Map<String, Object> snapshot(Location l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", l.getName());
        m.put("type", l.getType().name());
        m.put("contactId", l.getContactId());
        m.put("address", l.getAddress() == null ? null : l.getAddress().oneLine());
        m.put("parkingAvailable", l.isParkingAvailable());
        return m;
    }

    public record LocationData(UUID contactId, String type, String name, AddressData address, BigDecimal latitude,
            BigDecimal longitude, String accessInstructions, Boolean parkingAvailable,
            Map<String, DayHours> workingHours) {
    }

    public record Result(Location location, List<String> warnings) {
    }
}

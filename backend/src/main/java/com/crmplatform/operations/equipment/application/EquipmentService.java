package com.crmplatform.operations.equipment.application;

import com.crmplatform.crm.Contacts;
import com.crmplatform.operations.equipment.domain.Equipment;
import com.crmplatform.operations.equipment.domain.EquipmentRepository;
import com.crmplatform.operations.jobs.application.JobService;
import com.crmplatform.operations.jobs.domain.Job;
import com.crmplatform.operations.jobs.domain.JobRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.shared.web.SearchText;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Equipment register per customer site, and which devices a work order dealt with (R6). */
@Service
public class EquipmentService {

    private final EquipmentRepository equipment;
    private final JobRepository jobRepository;
    private final JobService jobs;
    private final Contacts contacts;
    private final AuditTrail audit;
    private final Clock clock;

    public EquipmentService(EquipmentRepository equipment, JobRepository jobRepository, JobService jobs,
            Contacts contacts, AuditTrail audit, Clock clock) {
        this.equipment = equipment;
        this.jobRepository = jobRepository;
        this.jobs = jobs;
        this.contacts = contacts;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Page<Equipment> search(EquipmentFilter f, Pageable pageable) {
        return equipment.findAll(matching(f), pageable);
    }

    @Transactional(readOnly = true)
    public Equipment get(UUID id) {
        return equipment.findById(id).orElseThrow(() -> ApiException.notFound("Equipment"));
    }

    @Transactional(readOnly = true)
    public List<Job> serviceHistory(UUID id) {
        get(id);
        return jobRepository.findByEquipment(id);
    }

    /** Devices of a job; the job access check applies (technicians: their own jobs only). */
    @Transactional(readOnly = true)
    public List<Equipment> ofJob(UUID jobId) {
        Job job = jobs.get(jobId);
        return equipment.findByIdIn(job.getEquipmentIds());
    }

    @Transactional(readOnly = true)
    public List<Equipment> findAll(Collection<UUID> ids) {
        return ids == null || ids.isEmpty() ? List.of() : equipment.findByIdIn(ids);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public Equipment create(EquipmentData d) {
        requireSite(d.contactId(), d.locationId());
        Equipment e = new Equipment(d.contactId(), d.locationId(), parseCategory(required(d.category(), "category")),
                required(d.name(), "name"));
        e.describe(d.manufacturer(), d.model(), d.serialNumber());
        e.setInstallation(d.installedOn(), installedJob(d.installedJobId(), d.locationId()));
        e.setStatus(d.status() == null ? Equipment.Status.ACTIVE : parseStatus(d.status()));
        e.setNotes(d.notes());
        requireUniqueSerial(e);
        Equipment saved = equipment.save(e);
        audit.created("EQUIPMENT", saved.getId(), null, saved.getInstalledJobId(), snapshot(saved));
        return saved;
    }

    @Transactional
    public Equipment update(UUID id, Patch<EquipmentData> patch) {
        Equipment e = get(id);
        EquipmentData d = patch.value();
        Map<String, Object> before = snapshot(e);
        if (patch.has("contactId") || patch.has("locationId")) {
            UUID contact = patch.has("contactId") ? d.contactId() : e.getContactId();
            UUID location = patch.has("locationId") ? d.locationId() : e.getLocationId();
            requireSite(contact, location);
            e.placeAt(contact, location);
        }
        patch.ifPresent("category", d.category(), v -> e.setCategory(parseCategory(required(v, "category"))));
        patch.ifPresent("name", d.name(), v -> e.setName(required(v, "name")));
        if (patch.has("manufacturer") || patch.has("model") || patch.has("serialNumber")) {
            e.describe(patch.has("manufacturer") ? d.manufacturer() : e.getManufacturer(),
                    patch.has("model") ? d.model() : e.getModel(),
                    patch.has("serialNumber") ? d.serialNumber() : e.getSerialNumber());
        }
        if (patch.has("installedOn") || patch.has("installedJobId")) {
            e.setInstallation(patch.has("installedOn") ? d.installedOn() : e.getInstalledOn(),
                    installedJob(patch.has("installedJobId") ? d.installedJobId() : e.getInstalledJobId(),
                            e.getLocationId()));
        }
        patch.ifPresent("status", d.status(), v -> e.setStatus(parseStatus(required(v, "status"))));
        patch.ifPresent("notes", d.notes(), e::setNotes);
        requireUniqueSerial(e);
        equipment.saveAndFlush(e);
        audit.updated("EQUIPMENT", id, null, e.getInstalledJobId(), before, snapshot(e));
        return e;
    }

    @Transactional
    public void delete(UUID id) {
        Equipment e = get(id);
        e.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        audit.deleted("EQUIPMENT", id, null, e.getInstalledJobId());
    }

    /** Records which devices the job dealt with; they must be at the job's site. */
    @Transactional
    public List<Equipment> setJobEquipment(UUID jobId, Collection<UUID> equipmentIds) {
        Job job = jobs.get(jobId);
        Set<UUID> ids = equipmentIds == null ? Set.of() : new HashSet<>(equipmentIds);
        if (ids.size() > 100) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A job can list at most 100 devices");
        }
        List<Equipment> found = equipment.findByIdIn(ids);
        if (found.size() != ids.size()) {
            throw ApiException.referenceNotFound("equipmentIds");
        }
        if (found.stream().anyMatch(e -> !e.getLocationId().equals(job.getLocationId()))) {
            throw ApiException.badRequest("EQUIPMENT_NOT_AT_SITE", "The device is not installed at this job's site");
        }
        Set<UUID> before = job.getEquipmentIds();
        job.replaceEquipment(ids);
        jobRepository.saveAndFlush(job);
        if (!before.equals(ids)) {
            audit.action("EQUIPMENT", "JOB", jobId, job.getProjectId(), jobId,
                    Map.of("equipmentIds", ids.stream().map(UUID::toString).sorted().toList()));
        }
        return found;
    }

    // ------------------------------------------------------------------ helpers

    private void requireSite(UUID contactId, UUID locationId) {
        if (contactId == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "contactId is required");
        }
        if (locationId == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "locationId is required");
        }
        contacts.find(contactId).orElseThrow(() -> ApiException.referenceNotFound("contactId"));
        Contacts.LocationSummary location = contacts.location(locationId)
                .orElseThrow(() -> ApiException.referenceNotFound("locationId"));
        if (!location.contactId().equals(contactId)) {
            throw ApiException.badRequest("LOCATION_NOT_OF_CUSTOMER", "The site does not belong to this customer");
        }
    }

    private UUID installedJob(UUID jobId, UUID locationId) {
        if (jobId == null) {
            return null;
        }
        Job job = jobRepository.findById(jobId).orElseThrow(() -> ApiException.referenceNotFound("installedJobId"));
        if (!job.getLocationId().equals(locationId)) {
            throw ApiException.badRequest("EQUIPMENT_NOT_AT_SITE", "The device is not installed at this job's site");
        }
        return jobId;
    }

    private void requireUniqueSerial(Equipment e) {
        if (e.getSerialNumber() != null && equipment.serialTaken(e.getSerialNumber(), e.getId())) {
            throw ApiException.conflict("DUPLICATE_SERIAL", "Another device already has this serial number");
        }
    }

    private static Equipment.Category parseCategory(String v) {
        try {
            return Equipment.Category.valueOf(v);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown equipment category");
        }
    }

    private static Equipment.Status parseStatus(String v) {
        try {
            return Equipment.Status.valueOf(v);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown equipment status");
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw ApiException.badRequest("VALIDATION_ERROR", field + " is required");
        }
        return value;
    }

    private static Specification<Equipment> matching(EquipmentFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.contactId() != null) {
                p.add(cb.equal(root.get("contactId"), f.contactId()));
            }
            if (f.locationId() != null) {
                p.add(cb.equal(root.get("locationId"), f.locationId()));
            }
            if (f.category() != null) {
                p.add(cb.equal(root.get("category"), parseCategory(f.category())));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), parseStatus(f.status())));
            }
            String like = Pagination.likePattern(f.q());
            if (like != null) {
                p.add(cb.or(cb.like(SearchText.column(cb, root.get("name")), like, '\\'),
                        cb.like(SearchText.column(cb, root.get("model")), like, '\\'),
                        cb.like(SearchText.column(cb, root.get("serialNumber")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static Map<String, Object> snapshot(Equipment e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", e.getName());
        m.put("category", e.getCategory().name());
        m.put("status", e.getStatus().name());
        m.put("serialNumber", e.getSerialNumber());
        m.put("locationId", e.getLocationId());
        return m;
    }

    public record EquipmentData(UUID contactId, UUID locationId, String category, String name, String manufacturer,
            String model, String serialNumber, LocalDate installedOn, UUID installedJobId, String status,
            String notes) {
    }

    public record EquipmentFilter(UUID contactId, UUID locationId, String category, String status, String q) {
    }
}

package com.crmplatform.operations.maintenance.application;

import com.crmplatform.crm.Contacts;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.operations.OperationsEvents.MaintenanceOverdue;
import com.crmplatform.operations.OperationsEvents.MaintenancePlanEnding;
import com.crmplatform.operations.equipment.domain.Equipment;
import com.crmplatform.operations.equipment.domain.EquipmentRepository;
import com.crmplatform.operations.jobs.application.JobService;
import com.crmplatform.operations.jobs.application.JobService.JobData;
import com.crmplatform.operations.jobs.domain.Job;
import com.crmplatform.operations.jobs.domain.JobRepository;
import com.crmplatform.operations.maintenance.domain.MaintenanceOccurrence;
import com.crmplatform.operations.maintenance.domain.MaintenancePlan;
import com.crmplatform.operations.maintenance.domain.MaintenanceRepositories.MaintenanceOccurrenceRepository;
import com.crmplatform.operations.maintenance.domain.MaintenanceRepositories.MaintenancePlanRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.shared.web.SearchText;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
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
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preventive-maintenance plans / agreements (R6, D3). Generation is idempotent: each (plan, due date)
 * produces at most one job, guarded by a lookup and the database's UNIQUE key.
 */
@Service
public class MaintenanceService {

    /** At most this many overdue due dates are caught up per plan in one run (no job floods). */
    static final int MAX_CATCH_UP = 3;
    /** Renewal reminder this many days before an agreement ends. */
    public static final int RENEWAL_NOTICE_DAYS = 30;

    private final MaintenancePlanRepository plans;
    private final MaintenanceOccurrenceRepository occurrences;
    private final EquipmentRepository equipment;
    private final JobRepository jobRepository;
    private final JobService jobs;
    private final Contacts contacts;
    private final WorkspaceMembers members;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MaintenanceService(MaintenancePlanRepository plans, MaintenanceOccurrenceRepository occurrences,
            EquipmentRepository equipment, JobRepository jobRepository, JobService jobs, Contacts contacts,
            WorkspaceMembers members, AuditTrail audit, ApplicationEventPublisher events, Clock clock) {
        this.plans = plans;
        this.occurrences = occurrences;
        this.equipment = equipment;
        this.jobRepository = jobRepository;
        this.jobs = jobs;
        this.contacts = contacts;
        this.members = members;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Page<MaintenancePlan> search(PlanFilter f, Pageable pageable) {
        return plans.findAll(matching(f), pageable);
    }

    @Transactional(readOnly = true)
    public MaintenancePlan get(UUID id) {
        return plans.findById(id).orElseThrow(() -> ApiException.notFound("Maintenance plan"));
    }

    @Transactional(readOnly = true)
    public List<OccurrenceView> occurrencesOf(UUID planId) {
        get(planId);
        return views(occurrences.findByPlanIdOrderByDueDateDesc(planId));
    }

    /** Preventive jobs past their due date that are still open. */
    @Transactional(readOnly = true)
    public List<OccurrenceView> overdue() {
        return views(occurrences.overdue(today()));
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public MaintenancePlan create(PlanData d) {
        requireSite(d.contactId(), d.locationId());
        MaintenancePlan p = new MaintenancePlan(d.contactId(), d.locationId(), required(d.title(), "title"));
        p.setDescription(d.description());
        p.setLeadDays(d.leadDays() == null ? 7 : d.leadDays());
        p.setResponseHours(d.responseHours());
        p.setIncludedVisits(d.includedVisits());
        p.setVisitBudget(d.visitBudget());
        p.assign(validAssignees(d.assigneeUserIds()));
        p.replaceEquipment(validEquipment(d.equipmentIds(), d.locationId()));
        LocalDate start = required(d.startDate(), "startDate");
        schedule(p, parseFrequency(required(d.frequency(), "frequency")), d.intervalDays(), start, d.endDate());
        MaintenancePlan saved = plans.save(p);
        audit.created("MAINTENANCE_PLAN", saved.getId(), null, null, snapshot(saved));
        return saved;
    }

    @Transactional
    public MaintenancePlan update(UUID id, Patch<PlanData> patch) {
        MaintenancePlan p = get(id);
        PlanData d = patch.value();
        Map<String, Object> before = snapshot(p);
        patch.ifPresent("title", d.title(), v -> p.setTitle(required(v, "title")));
        patch.ifPresent("description", d.description(), p::setDescription);
        patch.ifPresent("leadDays", d.leadDays(), v -> p.setLeadDays(v == null ? 7 : v));
        patch.ifPresent("responseHours", d.responseHours(), p::setResponseHours);
        patch.ifPresent("visitBudget", d.visitBudget(), p::setVisitBudget);
        patch.ifPresent("assigneeUserIds", d.assigneeUserIds(), v -> p.assign(validAssignees(v)));
        patch.ifPresent("equipmentIds", d.equipmentIds(), v -> p.replaceEquipment(validEquipment(v, p.getLocationId())));
        if (patch.has("status")) {
            p.setStatus(parseStatus(required(d.status(), "status")));
        }
        if (patch.has("frequency") || patch.has("intervalDays") || patch.has("startDate") || patch.has("endDate")
                || patch.has("includedVisits")) {
            if (patch.has("includedVisits")) {
                p.setIncludedVisits(d.includedVisits());
            }
            schedule(p, patch.has("frequency") ? parseFrequency(required(d.frequency(), "frequency")) : p.getFrequency(),
                    patch.has("intervalDays") ? d.intervalDays() : p.getIntervalDays(),
                    patch.has("startDate") ? required(d.startDate(), "startDate") : p.getStartDate(),
                    patch.has("endDate") ? d.endDate() : p.getEndDate());
        }
        plans.saveAndFlush(p);
        audit.updated("MAINTENANCE_PLAN", id, null, null, before, snapshot(p));
        return p;
    }

    @Transactional
    public void delete(UUID id) {
        MaintenancePlan p = get(id);
        p.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        audit.deleted("MAINTENANCE_PLAN", id, null, null);
    }

    /**
     * Generates the jobs that are due (inside the lead window), at most {@link #MAX_CATCH_UP} per run.
     * Running it again for the same day creates nothing. Returns the number of jobs created.
     */
    @Transactional
    public int generateDue(UUID planId) {
        MaintenancePlan p = plans.findById(planId).orElse(null);
        LocalDate today = today();
        if (p == null) {
            return 0;
        }
        int created = 0;
        int steps = 0;
        while (p.isDue(today) && steps++ < MAX_CATCH_UP) {
            LocalDate due = p.getNextDueDate();
            if (!occurrences.existsByPlanIdAndDueDate(planId, due)) {
                Job job = jobs.createForPlan(jobFor(p, due), planId, liveEquipment(p));
                occurrences.saveAndFlush(new MaintenanceOccurrence(planId, due, job.getId()));
                p.countGenerated();
                created++;
            }
            p.advance();
        }
        plans.saveAndFlush(p);
        if (created > 0) {
            audit.action("GENERATE", "MAINTENANCE_PLAN", planId, null, null, Map.of("jobs", created));
        }
        return created;
    }

    /** Workspace scope: overdue reminder for one occurrence, sent once. */
    @Transactional
    public boolean remindOverdue(UUID occurrenceId) {
        MaintenanceOccurrence o = occurrences.findById(occurrenceId).orElse(null);
        if (o == null || o.getOverdueNotifiedAt() != null) {
            return false;
        }
        o.overdueNotified(Instant.now(clock));
        MaintenancePlan p = plans.findById(o.getPlanId()).orElse(null);
        Set<UUID> recipients = new HashSet<>(members.managers());
        if (p != null) {
            recipients.addAll(p.getAssigneeUserIds());
        }
        if (!recipients.isEmpty()) {
            events.publishEvent(new MaintenanceOverdue(TenantContext.requireWorkspaceId(), o.getPlanId(),
                    p == null ? null : p.getTitle(), o.getJobId(), o.getDueDate(), recipients));
        }
        return true;
    }

    /** Workspace scope: renewal reminder for an agreement ending soon, sent once per end date. */
    @Transactional
    public boolean remindEnding(UUID planId) {
        MaintenancePlan p = plans.findById(planId).orElse(null);
        if (p == null || p.getRenewalReminderSentAt() != null || p.getEndDate() == null) {
            return false;
        }
        p.renewalReminded(Instant.now(clock));
        Set<UUID> recipients = members.managers();
        if (!recipients.isEmpty()) {
            events.publishEvent(new MaintenancePlanEnding(TenantContext.requireWorkspaceId(), p.getId(), p.getTitle(),
                    p.getContactId(), p.getEndDate(), recipients));
        }
        return true;
    }

    // ------------------------------------------------------------------ platform scope (scheduler)

    @Transactional(readOnly = true)
    public List<Object[]> plansDue() {
        return plans.dueForGeneration(today());
    }

    @Transactional(readOnly = true)
    public List<Object[]> occurrencesOverdue() {
        return occurrences.overdueNotNotified(today());
    }

    @Transactional(readOnly = true)
    public List<Object[]> plansEnding() {
        return plans.endingWithin(today(), RENEWAL_NOTICE_DAYS);
    }

    @Transactional
    public int endExpired() {
        return plans.endExpired(today());
    }

    // ------------------------------------------------------------------ helpers

    private void schedule(MaintenancePlan p, MaintenancePlan.Frequency frequency, Integer intervalDays,
            LocalDate start, LocalDate end) {
        if (frequency == MaintenancePlan.Frequency.CUSTOM && intervalDays == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "intervalDays is required for a custom frequency");
        }
        if (end != null && end.isBefore(start)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "endDate must be on or after startDate");
        }
        p.schedule(frequency, intervalDays, start, end, today());
    }

    private JobData jobFor(MaintenancePlan p, LocalDate due) {
        Set<UUID> assignees = p.getAssigneeUserIds().stream().filter(members::isActiveMember)
                .collect(Collectors.toSet());
        return new JobData(null, p.getTitle(), p.getDescription(), due, due, p.getVisitBudget(), null, null, null,
                null, null, null, p.getContactId(), p.getLocationId(), Job.Type.PREVENTIVE_MAINTENANCE.name(), Job.Priority.NORMAL.name(), null, null, assignees, null);
    }

    /** Plan devices still registered at the site (a removed device is skipped, not an error). */
    private Set<UUID> liveEquipment(MaintenancePlan p) {
        return equipment.findByIdIn(p.getEquipmentIds()).stream()
                .filter(e -> e.getLocationId().equals(p.getLocationId())).map(Equipment::getId)
                .collect(Collectors.toSet());
    }

    private List<OccurrenceView> views(List<MaintenanceOccurrence> list) {
        Set<UUID> jobIds = list.stream().map(MaintenanceOccurrence::getJobId).collect(Collectors.toSet());
        Set<UUID> planIds = list.stream().map(MaintenanceOccurrence::getPlanId).collect(Collectors.toSet());
        Map<UUID, Job> jobMap = jobRepository.findAllById(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, j -> j));
        Map<UUID, MaintenancePlan> planMap = plans.findAllById(planIds).stream()
                .collect(Collectors.toMap(MaintenancePlan::getId, x -> x));
        LocalDate today = today();
        return list.stream().map(o -> {
            Job j = jobMap.get(o.getJobId());
            MaintenancePlan p = planMap.get(o.getPlanId());
            boolean open = j != null && j.getStatus() != Job.Status.COMPLETED && j.getStatus() != Job.Status.CANCELLED;
            return new OccurrenceView(o.getId(), o.getPlanId(), p == null ? null : p.getTitle(), o.getDueDate(),
                    o.getJobId(), j == null ? null : j.getStatus().name(), open && o.getDueDate().isBefore(today));
        }).toList();
    }

    private void requireSite(UUID contactId, UUID locationId) {
        if (contactId == null || locationId == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "contactId and locationId are required");
        }
        contacts.find(contactId).orElseThrow(() -> ApiException.referenceNotFound("contactId"));
        Contacts.LocationSummary location = contacts.location(locationId)
                .orElseThrow(() -> ApiException.referenceNotFound("locationId"));
        if (!location.contactId().equals(contactId)) {
            throw ApiException.badRequest("LOCATION_NOT_OF_CUSTOMER", "The site does not belong to this customer");
        }
    }

    private Set<UUID> validAssignees(Collection<UUID> userIds) {
        Set<UUID> ids = userIds == null ? Set.of() : new HashSet<>(userIds);
        if (ids.size() > 20) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A job can have at most 20 assigned technicians");
        }
        for (UUID u : ids) {
            if (!members.isActiveMember(u)) {
                throw ApiException.referenceNotFound("assigneeUserIds");
            }
        }
        return ids;
    }

    private Set<UUID> validEquipment(Collection<UUID> ids, UUID locationId) {
        Set<UUID> set = ids == null ? Set.of() : new HashSet<>(ids);
        List<Equipment> found = equipment.findByIdIn(set);
        if (found.size() != set.size()) {
            throw ApiException.referenceNotFound("equipmentIds");
        }
        if (found.stream().anyMatch(e -> !e.getLocationId().equals(locationId))) {
            throw ApiException.badRequest("EQUIPMENT_NOT_AT_SITE", "The device is not installed at this job's site");
        }
        return set;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static MaintenancePlan.Frequency parseFrequency(String v) {
        try {
            return MaintenancePlan.Frequency.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown maintenance frequency");
        }
    }

    private static MaintenancePlan.Status parseStatus(String v) {
        try {
            return MaintenancePlan.Status.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown maintenance plan status");
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw ApiException.badRequest("VALIDATION_ERROR", field + " is required");
        }
        return value;
    }

    private static Specification<MaintenancePlan> matching(PlanFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.contactId() != null) {
                p.add(cb.equal(root.get("contactId"), f.contactId()));
            }
            if (f.locationId() != null) {
                p.add(cb.equal(root.get("locationId"), f.locationId()));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), parseStatus(f.status())));
            }
            String like = Pagination.likePattern(f.q());
            if (like != null) {
                p.add(cb.like(SearchText.column(cb, root.get("title")), like, '\\'));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static Map<String, Object> snapshot(MaintenancePlan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", p.getTitle());
        m.put("frequency", p.getFrequency().name());
        m.put("startDate", String.valueOf(p.getStartDate()));
        m.put("endDate", String.valueOf(p.getEndDate()));
        m.put("status", p.getStatus().name());
        m.put("responseHours", p.getResponseHours());
        return m;
    }

    public record PlanData(UUID contactId, UUID locationId, String title, String description, String frequency,
            Integer intervalDays, LocalDate startDate, LocalDate endDate, Integer leadDays, Integer responseHours,
            Integer includedVisits, BigDecimal visitBudget, String status, Set<UUID> assigneeUserIds,
            Set<UUID> equipmentIds) {
    }

    public record PlanFilter(UUID contactId, UUID locationId, String status, String q) {
    }

    public record OccurrenceView(UUID id, UUID planId, String planTitle, LocalDate dueDate, UUID jobId,
            String jobStatus, boolean overdue) {
    }
}

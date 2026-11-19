package com.crmplatform.operations.warranties.application;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.files.Files.FileSummary;
import com.crmplatform.operations.Jobs;
import com.crmplatform.operations.warranties.domain.Warranty;
import com.crmplatform.operations.warranties.domain.WarrantyRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.shared.web.Patch;
import com.crmplatform.work.Projects;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Warranties and their daily expiry (FR-OPS-06, FR-OPS-07, BR-064, BR-065). */
@Service
public class WarrantyService {

    static final String OWNER = "WARRANTY";

    private final WarrantyRepository warranties;
    private final Projects projects;
    private final Jobs jobs;
    private final LabelCatalog labels;
    private final Files files;
    private final AuditTrail audit;
    private final Clock clock;

    public WarrantyService(WarrantyRepository warranties, Projects projects, Jobs jobs, LabelCatalog labels,
            Files files, AuditTrail audit, Clock clock) {
        this.warranties = warranties;
        this.projects = projects;
        this.jobs = jobs;
        this.labels = labels;
        this.files = files;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Warranty> search(WarrantyFilter filter, Pageable pageable) {
        return warranties.findAll(WarrantySpecs.matching(filter), pageable);
    }

    @Transactional(readOnly = true)
    public Warranty get(UUID id) {
        return warranties.findById(id).orElseThrow(() -> ApiException.notFound("Warranty"));
    }

    @Transactional(readOnly = true)
    public List<FileSummary> documentsOf(UUID id) {
        return files.ownedBy(OWNER, id);
    }

    @Transactional
    public Warranty create(WarrantyData d) {
        validatePeriod(d.startDate(), d.endDate());
        Warranty w = new Warranty(d.title(), d.provider());
        w.setPeriod(d.startDate(), d.endDate());
        w.setNote(d.note());
        w.setStatus(d.status() == null ? Warranty.Status.ACTIVE : Warranty.Status.valueOf(d.status()));
        link(w, d.projectId(), d.jobId());
        Set<UUID> tags = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
        labels.requireTags(tags, "tagIds");
        w.replaceTags(tags);
        Warranty saved = warranties.save(w);
        files.attach(d.documentFileIds(), FileKind.DOCUMENT, OWNER, saved.getId(), "documentFileIds");
        audit.created("WARRANTY", saved.getId(), saved.getProjectId(), saved.getJobId(), snapshot(saved));
        return saved;
    }

    @Transactional
    public Warranty update(UUID id, Patch<WarrantyData> patch) {
        Warranty w = get(id);
        WarrantyData d = patch.value();
        Map<String, Object> before = snapshot(w);
        patch.ifPresent("title", d.title(), v -> w.setTitle(required(v, "title")));
        patch.ifPresent("provider", d.provider(), v -> w.setProvider(required(v, "provider")));
        patch.ifPresent("note", d.note(), w::setNote);
        patch.ifPresent("status", d.status(), v -> w.setStatus(Warranty.Status.valueOf(required(v, "status"))));
        if (patch.has("startDate") || patch.has("endDate")) {
            LocalDate start = patch.has("startDate") ? d.startDate() : w.getStartDate();
            LocalDate end = patch.has("endDate") ? d.endDate() : w.getEndDate();
            validatePeriod(start, end);
            w.setPeriod(start, end);
        }
        if (patch.has("projectId") || patch.has("jobId")) {
            link(w, patch.has("projectId") ? d.projectId() : w.getProjectId(),
                    patch.has("jobId") ? d.jobId() : w.getJobId());
        }
        if (patch.has("tagIds")) {
            Set<UUID> tags = d.tagIds() == null ? Set.of() : new HashSet<>(d.tagIds());
            labels.requireTags(tags, "tagIds");
            w.replaceTags(tags);
        }
        if (patch.has("documentFileIds")) {
            List<UUID> docs = d.documentFileIds() == null ? List.of() : d.documentFileIds();
            files.attach(docs, FileKind.DOCUMENT, OWNER, id, "documentFileIds");
            files.detachAllExcept(OWNER, id, docs);
        }
        warranties.saveAndFlush(w);
        audit.updated("WARRANTY", id, w.getProjectId(), w.getJobId(), before, snapshot(w));
        return w;
    }

    @Transactional
    public void delete(UUID id) {
        Warranty w = get(id);
        w.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        audit.deleted("WARRANTY", id, w.getProjectId(), w.getJobId());
    }

    /** BR-065: marks ACTIVE warranties whose end date has passed as EXPIRED; returns the count. */
    @Transactional
    public int expireEnded() {
        return warranties.expireEndedBefore(LocalDate.now(clock), Instant.now(clock));
    }

    /** Project deleted → its warranties are soft-deleted (BR-056). */
    @Transactional
    public void deleteAllOfProject(UUID projectId) {
        Instant now = Instant.now(clock);
        UUID actor = TenantContext.userId().orElse(null);
        warranties.findByProjectId(projectId).forEach(w -> w.markDeleted(now, actor));
    }

    /** Job deleted → warranties keep their project link but lose the job link. */
    @Transactional
    public void unlinkJob(UUID jobId) {
        warranties.findByJobId(jobId).forEach(w -> w.link(w.getProjectId(), null));
    }

    @Transactional
    public void detachTag(UUID tagId) {
        warranties.findByTag(tagId).forEach(w -> {
            Set<UUID> next = new HashSet<>(w.getTagIds());
            next.remove(tagId);
            w.replaceTags(next);
        });
    }

    // ------------------------------------------------------------------ helpers

    /** The job must belong to the project when both are given; a job alone implies its project. */
    private void link(Warranty w, UUID projectId, UUID jobId) {
        UUID project = projectId;
        if (jobId != null) {
            Jobs.JobSummary job = jobs.find(jobId).orElseThrow(() -> ApiException.referenceNotFound("jobId"));
            if (project != null && !Objects.equals(project, job.projectId())) {
                throw ApiException.badRequest("JOB_NOT_IN_PROJECT", "The job does not belong to the project");
            }
            project = job.projectId();
        }
        if (project != null && projects.find(project).isEmpty()) {
            throw ApiException.referenceNotFound("projectId");
        }
        w.link(project, jobId);
    }

    private static void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "startDate and endDate are required");
        }
        if (end.isBefore(start)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "endDate must be on or after startDate");
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw ApiException.badRequest("VALIDATION_ERROR", field + " is required");
        }
        return value;
    }

    private static Map<String, Object> snapshot(Warranty w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", w.getTitle());
        m.put("provider", w.getProvider());
        m.put("status", w.getStatus().name());
        m.put("startDate", String.valueOf(w.getStartDate()));
        m.put("endDate", String.valueOf(w.getEndDate()));
        m.put("projectId", w.getProjectId());
        m.put("jobId", w.getJobId());
        return m;
    }

    public record WarrantyData(String title, String provider, String status, String note, LocalDate startDate,
            LocalDate endDate, UUID projectId, UUID jobId, Set<UUID> tagIds, List<UUID> documentFileIds) {
    }

    public record WarrantyFilter(UUID projectId, UUID jobId, String status, String provider, LocalDate endingBefore,
            String q) {
    }
}

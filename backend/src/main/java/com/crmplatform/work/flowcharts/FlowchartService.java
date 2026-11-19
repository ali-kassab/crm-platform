package com.crmplatform.work.flowcharts;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.files.FileKind;
import com.crmplatform.files.Files;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.work.projects.domain.ProjectRepositories.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Flowcharts and their diagrams (FR-WRK-19/20, AZ-02). */
@Service
public class FlowchartService {

    static final int MAX_DIAGRAM_BYTES = 1024 * 1024;

    private final FlowchartRepository flowcharts;
    private final ProjectRepository projects;
    private final LabelCatalog labels;
    private final Files files;
    private final ObjectMapper mapper;
    private final AuditTrail audit;
    private final Clock clock;

    public FlowchartService(FlowchartRepository flowcharts, ProjectRepository projects, LabelCatalog labels,
            Files files, ObjectMapper mapper, AuditTrail audit, Clock clock) {
        this.flowcharts = flowcharts;
        this.projects = projects;
        this.labels = labels;
        this.files = files;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Flowchart> search(UUID projectId, Boolean favorite, boolean mine, String q, Pageable pageable) {
        return flowcharts.search(projectId, favorite, mine ? CurrentUser.id() : null, q, pageable);
    }

    @Transactional(readOnly = true)
    public Flowchart get(UUID id) {
        return flowcharts.findById(id).orElseThrow(() -> ApiException.notFound("Flowchart"));
    }

    @Transactional
    public Flowchart create(String title, String description, UUID projectId, UUID thumbnailFileId, Boolean favorite,
            Set<UUID> tagIds) {
        Flowchart f = new Flowchart(title, CurrentUser.id());
        apply(f, description, projectId, thumbnailFileId, favorite, tagIds, true, true, true, true, true);
        Flowchart saved = flowcharts.save(f);
        attachThumbnail(saved);
        audit.created("FLOWCHART", saved.getId(), projectId, null, Map.of("title", saved.getTitle()));
        return saved;
    }

    @Transactional
    public Flowchart update(UUID id, String title, String description, UUID projectId, UUID thumbnailFileId,
            Boolean favorite, Set<UUID> tagIds, Set<String> fields) {
        Flowchart f = requireEditable(id);
        if (fields.contains("title")) {
            if (title == null || title.trim().length() < 3) {
                throw ApiException.badRequest("VALIDATION_ERROR", "title must be 3–100 characters");
            }
            f.setTitle(title);
        }
        UUID previousThumb = f.getThumbnailFileId();
        apply(f, description, projectId, thumbnailFileId, favorite, tagIds, fields.contains("description"),
                fields.contains("projectId"), fields.contains("thumbnailFileId"), fields.contains("favorite"),
                fields.contains("tagIds"));
        if (fields.contains("thumbnailFileId")) {
            attachThumbnail(f);
            if (previousThumb != null && !previousThumb.equals(f.getThumbnailFileId())) {
                files.delete(previousThumb);
            }
        }
        return f;
    }

    @Transactional
    public void delete(UUID id) {
        Flowchart f = requireEditable(id);
        f.markDeleted(Instant.now(clock), TenantContext.userId().orElse(null));
        if (f.getThumbnailFileId() != null) {
            files.delete(f.getThumbnailFileId());
        }
        audit.deleted("FLOWCHART", id, f.getProjectId(), null);
    }

    /** Saves the diagram with optimistic concurrency (If-Match) and a 1 MB limit (TS-WRK-31). */
    @Transactional
    public Flowchart saveDiagram(UUID id, Map<String, Object> diagram, Long expectedVersion) {
        Flowchart f = requireEditable(id);
        if (expectedVersion == null) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED",
                    "Send the current version in the If-Match header");
        }
        if (f.getVersion() != expectedVersion) {
            throw new ApiException(HttpStatus.PRECONDITION_FAILED, "STALE_VERSION",
                    "The diagram was changed by someone else. Reload and try again.");
        }
        if (diagram == null || !(diagram.get("nodes") instanceof List) || !(diagram.get("edges") instanceof List)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A diagram needs nodes and edges arrays");
        }
        int size = mapper.writeValueAsString(diagram).getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_DIAGRAM_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "The diagram exceeds 1 MB");
        }
        f.setDiagram(diagram, size);
        flowcharts.saveAndFlush(f);
        return f;
    }

    private void apply(Flowchart f, String description, UUID projectId, UUID thumbnailFileId, Boolean favorite,
            Set<UUID> tagIds, boolean d, boolean p, boolean th, boolean fav, boolean tags) {
        if (d) {
            f.setDescription(description);
        }
        if (p) {
            if (projectId != null && !projects.existsById(projectId)) {
                throw ApiException.referenceNotFound("projectId");
            }
            f.setProjectId(projectId);
        }
        if (th) {
            f.setThumbnailFileId(thumbnailFileId);
        }
        if (fav) {
            f.setFavorite(Boolean.TRUE.equals(favorite));
        }
        if (tags) {
            Set<UUID> t = tagIds == null ? Set.of() : new HashSet<>(tagIds);
            labels.requireTags(t, "tagIds");
            f.replaceTags(t);
        }
    }

    private void attachThumbnail(Flowchart f) {
        files.attach(f.getThumbnailFileId() == null ? List.of() : List.of(f.getThumbnailFileId()), FileKind.IMAGE,
                "FLOWCHART", f.getId(), "thumbnailFileId");
    }

    /** Owner or Admin+ may change a flowchart (AZ-02). */
    private Flowchart requireEditable(UUID id) {
        Flowchart f = get(id);
        AuthenticatedUser user = CurrentUser.require();
        if (!f.getOwnerUserId().equals(user.userId()) && !user.isAdminOrOwner()) {
            throw ApiException.forbidden("Only the owner or an admin can change this flowchart");
        }
        return f;
    }
}

package com.crmplatform.work.flowcharts.web;

import com.crmplatform.crm.LabelCatalog;
import com.crmplatform.crm.LabelCatalog.LabelSummary;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patches;
import com.crmplatform.work.flowcharts.Flowchart;
import com.crmplatform.work.flowcharts.FlowchartService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
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
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Flowcharts and their diagrams (FR-WRK-19, FR-WRK-20). */
@RestController
@RequestMapping("/api/v1/flowcharts")
@Tag(name = "Flowcharts")
public class FlowchartController {

    private static final Map<String, String> SORT = Map.of("title", "title", "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final FlowchartService flowcharts;
    private final LabelCatalog labels;
    private final Patches patches;

    public FlowchartController(FlowchartService flowcharts, LabelCatalog labels, Patches patches) {
        this.flowcharts = flowcharts;
        this.labels = labels;
        this.patches = patches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('work:read')")
    public PageResponse<FlowchartResponse> list(@RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) Boolean favorite, @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Flowchart> result = flowcharts.search(projectId, favorite, mine, Pagination.likePattern(q), pageable);
        Map<UUID, LabelSummary> tags = tagsOf(result.getContent());
        return PageResponse.of(result.map(f -> FlowchartResponse.of(f, tags)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('work:read')")
    public FlowchartResponse get(@PathVariable UUID id) {
        return response(flowcharts.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('work:write')")
    public ResponseEntity<FlowchartResponse> create(@Valid @RequestBody FlowchartRequest body) {
        Flowchart f = flowcharts.create(body.title(), body.description(), body.projectId(), body.thumbnailFileId(),
                body.favorite(), body.tagIds());
        return ResponseEntity.created(URI.create("/api/v1/flowcharts/" + f.getId())).body(response(f));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('work:write')")
    public FlowchartResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        var patch = patches.read(body, FlowchartRequest.class);
        FlowchartRequest r = patch.value();
        flowcharts.update(id, r.title(), r.description(), r.projectId(), r.thumbnailFileId(), r.favorite(),
                r.tagIds(), patch.fields());
        return response(flowcharts.get(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('work:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        flowcharts.delete(id);
    }

    @GetMapping("/{id}/diagram")
    @PreAuthorize("hasAuthority('work:read')")
    public ResponseEntity<DiagramResponse> diagram(@PathVariable UUID id) {
        return diagramResponse(flowcharts.get(id));
    }

    /** Saves the diagram; {@code If-Match: "<version>"} is required (BR-086, TS-WRK-31). */
    @PutMapping("/{id}/diagram")
    @PreAuthorize("hasAuthority('work:write')")
    public ResponseEntity<DiagramResponse> saveDiagram(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody Map<String, Object> diagram) {
        flowcharts.saveDiagram(id, diagram, parseVersion(ifMatch));
        return diagramResponse(flowcharts.get(id));
    }

    // ------------------------------------------------------------------ helpers

    private static Long parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String v = ifMatch.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2);
        }
        v = v.replace("\"", "").trim();
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "If-Match must carry the numeric version");
        }
    }

    private static ResponseEntity<DiagramResponse> diagramResponse(Flowchart f) {
        return ResponseEntity.ok().eTag("\"" + f.getVersion() + "\"")
                .body(new DiagramResponse(f.getId(), f.getDiagram(), f.getDiagramSize(), f.getVersion(),
                        f.getUpdatedAt()));
    }

    private FlowchartResponse response(Flowchart f) {
        return FlowchartResponse.of(f, tagsOf(List.of(f)));
    }

    private Map<UUID, LabelSummary> tagsOf(Collection<Flowchart> list) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(f -> ids.addAll(f.getTagIds()));
        return labels.tags(ids);
    }

    // ------------------------------------------------------------------ DTOs

    public record FlowchartRequest(@NotBlank @Size(min = 3, max = 100) String title,
            @Size(max = 500) String description, UUID projectId, UUID thumbnailFileId, Boolean favorite,
            @Size(max = 20) Set<UUID> tagIds) {
    }

    public record FlowchartResponse(UUID id, String title, String description, UUID projectId, UUID thumbnailFileId,
            boolean favorite, UUID ownerUserId, List<LabelSummary> tags, int diagramSize, Instant createdAt,
            Instant updatedAt, long version) {

        static FlowchartResponse of(Flowchart f, Map<UUID, LabelSummary> tagMap) {
            List<LabelSummary> tags = new ArrayList<>(f.getTagIds().stream().map(tagMap::get)
                    .filter(Objects::nonNull).toList());
            tags.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            return new FlowchartResponse(f.getId(), f.getTitle(), f.getDescription(), f.getProjectId(),
                    f.getThumbnailFileId(), f.isFavorite(), f.getOwnerUserId(), tags, f.getDiagramSize(),
                    f.getCreatedAt(), f.getUpdatedAt(), f.getVersion());
        }
    }

    public record DiagramResponse(UUID id, Map<String, Object> diagram, int sizeBytes, long version,
            Instant updatedAt) {
    }
}

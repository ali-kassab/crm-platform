package com.crmplatform.work.boards.web;

import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import com.crmplatform.shared.web.Patches;
import com.crmplatform.work.boards.application.BoardService;
import com.crmplatform.work.boards.domain.BoardTemplate;
import com.crmplatform.work.boards.domain.BoardTemplate.TemplateColumn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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

/** Board templates; at most one default per workspace (FR-WRK-18, BR-080). */
@RestController
@RequestMapping("/api/v1/board-templates")
@Tag(name = "Board templates")
public class BoardTemplateController {

    private static final Map<String, String> SORT = Map.of("title", "title", "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final BoardService boards;
    private final Patches patches;

    public BoardTemplateController(BoardService boards, Patches patches) {
        this.boards = boards;
        this.patches = patches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('work:read')")
    public PageResponse<TemplateResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.ASC, "title"));
        return PageResponse.of(boards.listTemplates(Pagination.likePattern(q), pageable), TemplateResponse::of);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('work:read')")
    public TemplateResponse get(@PathVariable UUID id) {
        return TemplateResponse.of(boards.template(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('templates:manage')")
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody TemplateRequest body) {
        BoardTemplate t = boards.createTemplate(body.title(), body.description(), body.columns(),
                body.defaultColumnIndex() == null ? 0 : body.defaultColumnIndex(), Boolean.TRUE.equals(body.isDefault()));
        return ResponseEntity.created(URI.create("/api/v1/board-templates/" + t.getId())).body(TemplateResponse.of(t));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('templates:manage')")
    public TemplateResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        var patch = patches.read(body, TemplateRequest.class);
        TemplateRequest r = patch.value();
        BoardTemplate current = boards.template(id);
        List<String> currentColumns = current.getColumns().stream().map(TemplateColumn::getTitle).toList();
        int currentDefault = 0;
        for (int i = 0; i < current.getColumns().size(); i++) {
            if (current.getColumns().get(i).isDefault()) {
                currentDefault = i;
            }
        }
        BoardTemplate t = boards.updateTemplate(id,
                patch.has("title") ? r.title() : current.getTitle(),
                patch.has("description") ? r.description() : current.getDescription(),
                patch.has("columns") ? r.columns() : currentColumns,
                patch.has("defaultColumnIndex") && r.defaultColumnIndex() != null ? r.defaultColumnIndex()
                        : patch.has("columns") ? 0 : currentDefault,
                patch.has("isDefault") ? r.isDefault() : null);
        return TemplateResponse.of(t);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('templates:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        boards.deleteTemplate(id);
    }

    public record TemplateRequest(@NotBlank @Size(max = 100) String title, @Size(max = 500) String description,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 30) String> columns,
            @Min(0) Integer defaultColumnIndex, Boolean isDefault) {
    }

    public record ColumnResponse(String title, int position, boolean isDefault) {
    }

    public record TemplateResponse(UUID id, String title, String description, boolean isDefault,
            List<ColumnResponse> columns, Instant createdAt, Instant updatedAt, long version) {

        static TemplateResponse of(BoardTemplate t) {
            return new TemplateResponse(t.getId(), t.getTitle(), t.getDescription(), t.isDefault(),
                    t.getColumns().stream().map(c -> new ColumnResponse(c.getTitle(), c.getPosition(), c.isDefault()))
                            .toList(),
                    t.getCreatedAt(), t.getUpdatedAt(), t.getVersion());
        }
    }
}

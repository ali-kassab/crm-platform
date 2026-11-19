package com.crmplatform.crm.refdata.web;

import com.crmplatform.crm.refdata.application.RefDataService;
import com.crmplatform.crm.refdata.domain.Category;
import com.crmplatform.crm.refdata.domain.Label;
import com.crmplatform.crm.refdata.domain.Label.LabelValues;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

/** Categories, statuses and tags (FR-CRM-01..03). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reference data")
public class RefDataController {

    private static final Map<String, String> SORT = Map.of("name", "name", "createdAt", "createdAt");
    private static final String COLOR = "^#[0-9A-Fa-f]{6}$";
    private static final String ICON = "^[a-z0-9-]{1,40}$";

    private final RefDataService service;

    public RefDataController(RefDataService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------ categories

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('refdata:read')")
    public PageResponse<CategoryResponse> categories(@RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by("name"));
        return PageResponse.of(service.listCategories(Pagination.likePattern(q), pageable), CategoryResponse::of);
    }

    @GetMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('refdata:read')")
    public CategoryResponse category(@PathVariable UUID id) {
        return CategoryResponse.of(service.category(id));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('refdata:manage')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest body) {
        Category c = service.createCategory(body.name(), body.description());
        return ResponseEntity.created(URI.create("/api/v1/categories/" + c.getId())).body(CategoryResponse.of(c));
    }

    @PatchMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('refdata:manage')")
    public CategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest body) {
        return CategoryResponse.of(service.updateCategory(id, body.name(), body.description()));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('refdata:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID id) {
        service.deleteCategory(id);
    }

    // ------------------------------------------------------------------ statuses

    @GetMapping("/statuses")
    @PreAuthorize("hasAuthority('refdata:read')")
    public PageResponse<LabelResponse> statuses(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by("name"));
        return PageResponse.of(service.listStatuses(Pagination.likePattern(q), categoryId, pageable), LabelResponse::of);
    }

    @GetMapping("/statuses/{id}")
    @PreAuthorize("hasAuthority('refdata:read')")
    public LabelResponse status(@PathVariable UUID id) {
        return LabelResponse.of(service.statusEntity(id));
    }

    @PostMapping("/statuses")
    @PreAuthorize("hasAuthority('refdata:manage')")
    public ResponseEntity<LabelResponse> createStatus(@Valid @RequestBody StatusRequest body) {
        var s = service.createStatus(body.values());
        return ResponseEntity.created(URI.create("/api/v1/statuses/" + s.getId())).body(LabelResponse.of(s));
    }

    @PatchMapping("/statuses/{id}")
    @PreAuthorize("hasAuthority('refdata:manage')")
    public LabelResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest body) {
        return LabelResponse.of(service.updateStatus(id, body.values()));
    }

    @DeleteMapping("/statuses/{id}")
    @PreAuthorize("hasAuthority('refdata:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStatus(@PathVariable UUID id) {
        service.deleteStatus(id);
    }

    // ------------------------------------------------------------------ tags

    @GetMapping("/tags")
    @PreAuthorize("hasAuthority('refdata:read')")
    public PageResponse<LabelResponse> tags(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by("name"));
        return PageResponse.of(service.listTags(Pagination.likePattern(q), categoryId, pageable), LabelResponse::of);
    }

    @GetMapping("/tags/{id}")
    @PreAuthorize("hasAuthority('refdata:read')")
    public LabelResponse tag(@PathVariable UUID id) {
        return LabelResponse.of(service.tagEntity(id));
    }

    @PostMapping("/tags")
    @PreAuthorize("hasAuthority('refdata:manage')")
    public ResponseEntity<LabelResponse> createTag(@Valid @RequestBody TagRequest body) {
        var t = service.createTag(body.values());
        return ResponseEntity.created(URI.create("/api/v1/tags/" + t.getId())).body(LabelResponse.of(t));
    }

    @PatchMapping("/tags/{id}")
    @PreAuthorize("hasAuthority('refdata:manage')")
    public LabelResponse updateTag(@PathVariable UUID id, @Valid @RequestBody TagRequest body) {
        return LabelResponse.of(service.updateTag(id, body.values()));
    }

    @DeleteMapping("/tags/{id}")
    @PreAuthorize("hasAuthority('refdata:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@PathVariable UUID id) {
        service.deleteTag(id);
    }

    // ------------------------------------------------------------------ DTOs

    public record CategoryRequest(@NotBlank @Size(max = 50) String name, @Size(max = 500) String description) {
    }

    public record CategoryResponse(UUID id, String name, String description, Instant createdAt, long version) {
        static CategoryResponse of(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getCreatedAt(), c.getVersion());
        }
    }

    public record StatusRequest(
            @NotBlank @Size(min = 3, max = 25) String name,
            @NotNull UUID categoryId,
            @Size(max = 500) String description,
            @Pattern(regexp = COLOR) String backgroundColor,
            @Pattern(regexp = COLOR) String textColor,
            @Pattern(regexp = ICON) String icon,
            UUID startProcedureId,
            UUID stopProcedureId) {
        LabelValues values() {
            return new LabelValues(name, categoryId, description, backgroundColor, textColor, icon, startProcedureId,
                    stopProcedureId);
        }
    }

    public record TagRequest(
            @NotBlank @Size(max = 50) String name,
            @NotNull UUID categoryId,
            @Size(max = 500) String description,
            @Pattern(regexp = COLOR) String backgroundColor,
            @Pattern(regexp = COLOR) String textColor,
            @Pattern(regexp = ICON) String icon,
            UUID startProcedureId,
            UUID stopProcedureId) {
        LabelValues values() {
            return new LabelValues(name, categoryId, description, backgroundColor, textColor, icon, startProcedureId,
                    stopProcedureId);
        }
    }

    public record LabelResponse(UUID id, String name, UUID categoryId, String description, String backgroundColor,
            String textColor, String icon, UUID startProcedureId, UUID stopProcedureId, Instant createdAt,
            long version) {
        static LabelResponse of(Label l) {
            return new LabelResponse(l.getId(), l.getName(), l.getCategoryId(), l.getDescription(),
                    l.getBackgroundColor(), l.getTextColor(), l.getIcon(), l.getStartProcedureId(),
                    l.getStopProcedureId(), l.getCreatedAt(), l.getVersion());
        }
    }
}

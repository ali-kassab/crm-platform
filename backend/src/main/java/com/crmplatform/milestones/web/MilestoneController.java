package com.crmplatform.milestones.web;

import com.crmplatform.milestones.MilestoneOwner.OwnerType;
import com.crmplatform.milestones.Milestones.MilestoneInput;
import com.crmplatform.milestones.Milestones.MilestoneView;
import com.crmplatform.milestones.Milestones.StepInput;
import com.crmplatform.milestones.application.MilestoneService;
import com.crmplatform.milestones.domain.MilestoneTemplate;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Milestones on contacts, locations, projects, jobs and tasks plus milestone templates. Permission
 * follows the owning module (read/write of crm, work or ops).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Milestones")
public class MilestoneController {

    private final MilestoneService service;

    public MilestoneController(MilestoneService service) {
        this.service = service;
    }

    @GetMapping("/{ownerPath:contacts|locations|projects|jobs|tasks}/{ownerId}/milestone")
    public MilestoneView get(@PathVariable String ownerPath, @PathVariable UUID ownerId) {
        OwnerType type = owner(ownerPath, false);
        service.requireOwner(type, ownerId);
        return service.find(type, ownerId).orElseThrow(() -> ApiException.notFound("Milestone"));
    }

    @PutMapping("/{ownerPath:contacts|locations|projects|jobs|tasks}/{ownerId}/milestone")
    public MilestoneView set(@PathVariable String ownerPath, @PathVariable UUID ownerId,
            @Valid @RequestBody MilestoneRequest body) {
        OwnerType type = owner(ownerPath, true);
        service.requireOwner(type, ownerId);
        if (body.templateId() != null) {
            return service.setFromTemplate(type, ownerId, body.templateId());
        }
        if (body.name() == null || body.name().isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "name or templateId is required");
        }
        return service.set(type, ownerId, new MilestoneInput(body.name(), body.description(), body.categoryId(),
                body.steps() == null ? List.of() : body.steps().stream().map(StepRequest::toInput).toList()));
    }

    @DeleteMapping("/{ownerPath:contacts|locations|projects|jobs|tasks}/{ownerId}/milestone")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable String ownerPath, @PathVariable UUID ownerId) {
        OwnerType type = owner(ownerPath, true);
        service.requireOwner(type, ownerId);
        service.clear(type, ownerId);
    }

    @PostMapping("/{ownerPath:contacts|locations|projects|jobs|tasks}/{ownerId}/milestone/steps/{stepId}/complete")
    public MilestoneView complete(@PathVariable String ownerPath, @PathVariable UUID ownerId, @PathVariable UUID stepId) {
        OwnerType type = owner(ownerPath, true);
        service.requireOwner(type, ownerId);
        return service.complete(type, ownerId, stepId);
    }

    @PostMapping("/{ownerPath:contacts|locations|projects|jobs|tasks}/{ownerId}/milestone/steps/{stepId}/reopen")
    public MilestoneView reopen(@PathVariable String ownerPath, @PathVariable UUID ownerId, @PathVariable UUID stepId) {
        OwnerType type = owner(ownerPath, true);
        service.requireOwner(type, ownerId);
        return service.reopen(type, ownerId, stepId);
    }

    // ------------------------------------------------------------------ templates

    @GetMapping("/milestone-templates")
    @PreAuthorize("hasAuthority('work:read')")
    public PageResponse<TemplateResponse> templates(@RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, Map.of("name", "name", "createdAt", "createdAt"),
                Sort.by("name"));
        return PageResponse.of(service.listTemplates(Pagination.likePattern(q), pageable), TemplateResponse::of);
    }

    @GetMapping("/milestone-templates/{id}")
    @PreAuthorize("hasAuthority('work:read')")
    public TemplateResponse template(@PathVariable UUID id) {
        return TemplateResponse.of(service.template(id));
    }

    @PostMapping("/milestone-templates")
    @PreAuthorize("hasAuthority('templates:manage')")
    public ResponseEntity<TemplateResponse> createTemplate(@Valid @RequestBody TemplateRequest body) {
        MilestoneTemplate t = service.createTemplate(body.name(), body.description(), body.categoryId(), body.inputs());
        return ResponseEntity.created(URI.create("/api/v1/milestone-templates/" + t.getId())).body(TemplateResponse.of(t));
    }

    @PatchMapping("/milestone-templates/{id}")
    @PreAuthorize("hasAuthority('templates:manage')")
    public TemplateResponse updateTemplate(@PathVariable UUID id, @Valid @RequestBody TemplateRequest body) {
        return TemplateResponse.of(service.updateTemplate(id, body.name(), body.description(), body.categoryId(),
                body.inputs()));
    }

    @DeleteMapping("/milestone-templates/{id}")
    @PreAuthorize("hasAuthority('templates:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable UUID id) {
        service.deleteTemplate(id);
    }

    private static OwnerType owner(String path, boolean write) {
        OwnerType type = OwnerType.fromPath(path);
        if (type == null) {
            throw ApiException.notFound("Resource");
        }
        String permission = write ? type.writePermission() : type.readPermission();
        if (!CurrentUser.require().has(permission)) {
            throw ApiException.forbidden("You do not have permission to perform this action");
        }
        return type;
    }

    // ------------------------------------------------------------------ DTOs

    public record StepRequest(
            Integer order,
            @NotBlank @Size(max = 100) String label,
            @Pattern(regexp = "^[a-z0-9-]{1,40}$") String icon,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String backgroundColor,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String textColor,
            @Pattern(regexp = "^(ICON|IMAGE)$") String viewMode,
            UUID imageFileId,
            LocalDate startDate,
            LocalDate endDate) {
        StepInput toInput() {
            return new StepInput(order, label, icon, backgroundColor, textColor, viewMode, imageFileId, startDate,
                    endDate);
        }
    }

    public record MilestoneRequest(UUID templateId, @Size(max = 100) String name, @Size(max = 1000) String description,
            UUID categoryId, @Size(max = 30) List<@Valid StepRequest> steps) {
    }

    public record TemplateRequest(@NotBlank @Size(max = 100) String name, @Size(max = 1000) String description,
            UUID categoryId, @Size(min = 2, max = 30) List<@Valid StepRequest> steps) {
        List<StepInput> inputs() {
            return steps == null ? List.of() : steps.stream().map(StepRequest::toInput).toList();
        }
    }

    public record TemplateResponse(UUID id, String name, String description, UUID categoryId,
            List<MilestoneTemplate.TemplateStep> steps, long version) {
        static TemplateResponse of(MilestoneTemplate t) {
            return new TemplateResponse(t.getId(), t.getName(), t.getDescription(), t.getCategoryId(), t.getSteps(),
                    t.getVersion());
        }
    }
}

package com.crmplatform.crm.notes.web;

import com.crmplatform.crm.notes.application.NoteService;
import com.crmplatform.crm.notes.domain.Note;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

@RestController
@RequestMapping("/api/v1/notes")
@Tag(name = "Notes")
public class NoteController {

    private final NoteService notes;

    public NoteController(NoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:read')")
    public PageResponse<NoteResponse> list(@RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, Map.of("createdAt", "createdAt"),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(notes.list(contactId, projectId, jobId, pageable), NoteResponse::of);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:read')")
    public NoteResponse get(@PathVariable UUID id) {
        return NoteResponse.of(notes.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:write')")
    public ResponseEntity<NoteResponse> create(@Valid @RequestBody NoteRequest body) {
        Note n = notes.create(body.contactId(), body.projectId(), body.jobId(), body.title(), body.body(),
                body.documentFileId());
        return ResponseEntity.created(URI.create("/api/v1/notes/" + n.getId())).body(NoteResponse.of(n));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:write')")
    public NoteResponse update(@PathVariable UUID id, @RequestBody JsonNode body) {
        String title = text(body, "title", 3, 100);
        String text = text(body, "body", 1, 5000);
        boolean documentSent = body.has("documentFileId");
        UUID documentFileId = documentSent && !body.get("documentFileId").isNull()
                ? UUID.fromString(body.get("documentFileId").asString()) : null;
        return NoteResponse.of(notes.update(id, title, text, documentSent, documentFileId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        notes.delete(id);
    }

    private static String text(JsonNode body, String field, int min, int max) {
        if (!body.has(field)) {
            return null;
        }
        String value = body.get(field).isNull() ? "" : body.get(field).asString().trim();
        if (value.length() < min || value.length() > max) {
            throw com.crmplatform.shared.error.ApiException.badRequest("VALIDATION_ERROR",
                    field + " must be " + min + "–" + max + " characters");
        }
        return value;
    }

    public record NoteRequest(UUID contactId, UUID projectId, UUID jobId,
            @NotBlank @Size(min = 3, max = 100) String title, @NotBlank @Size(max = 5000) String body,
            UUID documentFileId) {
    }

    public record NoteResponse(UUID id, UUID contactId, UUID projectId, UUID jobId, String title, String body,
            UUID documentFileId, UUID createdBy, Instant createdAt, Instant updatedAt, long version) {
        static NoteResponse of(Note n) {
            return new NoteResponse(n.getId(), n.getContactId(), n.getProjectId(), n.getJobId(), n.getTitle(),
                    n.getBody(), n.getDocumentFileId(), n.getCreatedBy(), n.getCreatedAt(), n.getUpdatedAt(),
                    n.getVersion());
        }
    }
}

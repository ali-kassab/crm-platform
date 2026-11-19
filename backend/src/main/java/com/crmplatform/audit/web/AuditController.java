package com.crmplatform.audit.web;

import com.crmplatform.audit.application.AuditQueries;
import com.crmplatform.audit.domain.AuditEntry;
import com.crmplatform.shared.tenant.TenantContext;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only audit log: the caller's workspace, or platform-level entries for platform admins. */
@RestController
@Tag(name = "Audit")
public class AuditController {

    private static final Map<String, String> SORT = Map.of("occurredAt", "occurredAt");

    private final AuditQueries audit;

    public AuditController(AuditQueries audit) {
        this.audit = audit;
    }

    @GetMapping("/api/v1/audit")
    @PreAuthorize("hasAuthority('audit:read')")
    public PageResponse<AuditEntryResponse> workspace(@RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId, @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID jobId, @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return PageResponse.of(audit.workspace(TenantContext.requireWorkspaceId(), entityType, entityId, projectId,
                jobId, actorUserId, from, to, pageable), AuditEntryResponse::of);
    }

    @GetMapping("/api/v1/admin/audit")
    @PreAuthorize("hasAuthority('platform:admin')")
    public PageResponse<AuditEntryResponse> platform(@RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return PageResponse.of(audit.platform(entityType, entityId, from, to, pageable), AuditEntryResponse::of);
    }

    public record AuditEntryResponse(UUID id, UUID actorUserId, String actorLabel, String action, String entityType,
            UUID entityId, UUID projectId, UUID jobId, Map<String, Object> changes, String traceId,
            Instant occurredAt) {

        static AuditEntryResponse of(AuditEntry a) {
            return new AuditEntryResponse(a.getId(), a.getActorUserId(), a.getActorLabel(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getProjectId(), a.getJobId(), a.getChanges(),
                    a.getTraceId(), a.getOccurredAt());
        }
    }
}

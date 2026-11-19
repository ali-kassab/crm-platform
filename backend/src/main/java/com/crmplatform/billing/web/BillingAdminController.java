package com.crmplatform.billing.web;

import com.crmplatform.billing.application.BillingAdminService;
import com.crmplatform.billing.application.BillingAdminService.Action;
import com.crmplatform.billing.application.BillingAdminService.MethodData;
import com.crmplatform.billing.application.BillingAdminService.PlanData;
import com.crmplatform.billing.application.BillingAdminService.WorkspaceRow;
import com.crmplatform.billing.domain.Plan;
import com.crmplatform.billing.domain.SubscriptionEvent;
import com.crmplatform.billing.domain.SubscriptionRequest;
import com.crmplatform.billing.web.SubscriptionController.MethodView;
import com.crmplatform.billing.web.SubscriptionController.PlanView;
import com.crmplatform.billing.web.SubscriptionController.RequestView;
import com.crmplatform.billing.web.SubscriptionController.SubscriptionResponse;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.security.CurrentUser;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform admin: subscriptions, plans, payment instructions and payment-request review. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Platform admin — billing")
@PreAuthorize("hasAuthority('platform:admin')")
public class BillingAdminController {

    private static final Map<String, String> REQUEST_SORT = Map.of("createdAt", "createdAt");

    private final BillingAdminService admin;

    public BillingAdminController(BillingAdminService admin) {
        this.admin = admin;
    }

    // ------------------------------------------------------------------ plans

    @GetMapping("/plans")
    public List<AdminPlanView> plans() {
        return admin.plans().stream().map(AdminPlanView::of).toList();
    }

    @PostMapping("/plans")
    public ResponseEntity<AdminPlanView> createPlan(@Valid @RequestBody PlanRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminPlanView.of(admin.createPlan(CurrentUser.id(), body.toData(true))));
    }

    @PatchMapping("/plans/{id}")
    public AdminPlanView updatePlan(@PathVariable UUID id, @Valid @RequestBody PlanRequest body) {
        return AdminPlanView.of(admin.updatePlan(CurrentUser.id(), id, body.toData(body.limitsGiven())));
    }

    // ------------------------------------------------------------------ payment methods

    @GetMapping("/payment-methods")
    public List<AdminMethodView> methods() {
        return admin.methods().stream().map(AdminMethodView::of).toList();
    }

    @PostMapping("/payment-methods")
    public ResponseEntity<AdminMethodView> createMethod(@Valid @RequestBody MethodRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminMethodView.of(admin.saveMethod(CurrentUser.id(), null, body.toData())));
    }

    @PatchMapping("/payment-methods/{id}")
    public AdminMethodView updateMethod(@PathVariable UUID id, @Valid @RequestBody MethodRequest body) {
        return AdminMethodView.of(admin.saveMethod(CurrentUser.id(), id, body.toData()));
    }

    // ------------------------------------------------------------------ requests

    @GetMapping("/subscription-requests")
    public PageResponse<AdminRequestView> requests(@RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        SubscriptionRequest.Status s;
        try {
            s = status == null ? null : SubscriptionRequest.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown request status");
        }
        var pageable = Pagination.pageable(page, size, null, REQUEST_SORT, Sort.by(Sort.Direction.ASC, "createdAt"));
        return PageResponse.of(admin.requests(s, pageable), AdminRequestView::of);
    }

    @GetMapping("/subscription-requests/{id}")
    public AdminRequestView request(@PathVariable UUID id) {
        return AdminRequestView.of(admin.request(id));
    }

    @GetMapping("/subscription-requests/{id}/receipt-url")
    public Map<String, String> receiptUrl(@PathVariable UUID id) {
        return Map.of("url", admin.receiptUrl(CurrentUser.id(), id));
    }

    @PostMapping("/subscription-requests/{id}/approve")
    public AdminRequestView approve(@PathVariable UUID id, @Valid @RequestBody(required = false) ReviewRequest body) {
        return AdminRequestView.of(admin.approve(CurrentUser.id(), id, body == null ? null : body.note()));
    }

    @PostMapping("/subscription-requests/{id}/reject")
    public AdminRequestView reject(@PathVariable UUID id, @Valid @RequestBody ReviewRequest body) {
        return AdminRequestView.of(admin.reject(CurrentUser.id(), id, body.note()));
    }

    // ------------------------------------------------------------------ workspaces

    @GetMapping("/workspaces")
    public List<WorkspaceRow> workspaces(@RequestParam(required = false) String status,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return admin.workspaces(status, q, limit, offset);
    }

    @GetMapping("/workspaces/{id}")
    public WorkspaceView workspace(@PathVariable UUID id) {
        var d = admin.workspace(id);
        return new WorkspaceView(d.workspaceId(), SubscriptionResponse.of(d.overview()),
                d.history().stream().map(EventView::of).toList(), d.requests().stream().map(RequestView::of).toList());
    }

    @PostMapping("/workspaces/{id}/subscription")
    public SubscriptionResponse act(@PathVariable UUID id, @Valid @RequestBody ActionRequest body) {
        Action action;
        try {
            action = Action.valueOf(body.action());
        } catch (RuntimeException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Unknown subscription action");
        }
        admin.act(CurrentUser.id(), id, action, body.days(), body.planId(), body.note());
        return SubscriptionResponse.of(admin.workspace(id).overview());
    }

    // ------------------------------------------------------------------ DTOs

    public record PlanRequest(String code, @Size(max = 100) String nameAr, @Size(max = 100) String nameEn,
            @Size(max = 1000) String descriptionAr, @Size(max = 1000) String descriptionEn,
            @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal priceMonthly,
            @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal priceYearly,
            @Min(1) Integer maxUsers, @Min(0) Integer maxActiveProjects, @Min(0) Integer maxJobsPerMonth,
            @Min(0) Integer maxStorageMb, Boolean active, Integer sortOrder) {

        boolean limitsGiven() {
            return maxUsers != null || maxActiveProjects != null || maxJobsPerMonth != null || maxStorageMb != null;
        }

        PlanData toData(boolean limits) {
            return new PlanData(code, nameAr, nameEn, descriptionAr, descriptionEn, priceMonthly, priceYearly,
                    maxUsers, maxActiveProjects, maxJobsPerMonth, maxStorageMb, limits, active, sortOrder);
        }
    }

    public record MethodRequest(String kind, @Size(max = 100) String labelAr, @Size(max = 100) String labelEn,
            @Size(max = 150) String account, @Size(max = 2000) String instructionsAr,
            @Size(max = 2000) String instructionsEn, Boolean active, Integer sortOrder) {
        MethodData toData() {
            return new MethodData(kind, labelAr, labelEn, account, instructionsAr, instructionsEn, active, sortOrder);
        }
    }

    public record ReviewRequest(@Size(max = 1000) String note) {
    }

    public record ActionRequest(String action, Integer days, UUID planId, @Size(max = 1000) String note) {
    }

    public record AdminPlanView(PlanView plan, boolean active, int sortOrder) {
        static AdminPlanView of(Plan p) {
            return new AdminPlanView(PlanView.of(p), p.isActive(), p.getSortOrder());
        }
    }

    public record AdminMethodView(MethodView method, boolean active, int sortOrder) {
        static AdminMethodView of(com.crmplatform.billing.domain.PaymentMethod m) {
            return new AdminMethodView(MethodView.of(m), m.isActive(), m.getSortOrder());
        }
    }

    public record AdminRequestView(UUID workspaceId, UUID requestedBy, UUID reviewedBy, RequestView request) {
        static AdminRequestView of(SubscriptionRequest r) {
            return new AdminRequestView(r.getWorkspaceId(), r.getRequestedBy(), r.getReviewedBy(), RequestView.of(r));
        }
    }

    public record EventView(String type, String fromStatus, String toStatus, String actorKind, UUID actorUserId,
            Map<String, Object> detail, Instant occurredAt) {
        static EventView of(SubscriptionEvent e) {
            return new EventView(e.getType(), e.getFromStatus(), e.getToStatus(), e.getActorKind(), e.getActorUserId(),
                    e.getDetail(), e.getOccurredAt());
        }
    }

    public record WorkspaceView(UUID workspaceId, SubscriptionResponse subscription, List<EventView> history,
            List<RequestView> requests) {
    }
}

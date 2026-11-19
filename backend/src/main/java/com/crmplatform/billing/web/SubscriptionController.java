package com.crmplatform.billing.web;

import com.crmplatform.billing.application.SubscriptionService;
import com.crmplatform.billing.application.SubscriptionService.Overview;
import com.crmplatform.billing.application.SubscriptionService.RequestData;
import com.crmplatform.billing.domain.PaymentMethod;
import com.crmplatform.billing.domain.Plan;
import com.crmplatform.billing.domain.SubscriptionRequest;
import com.crmplatform.shared.security.AuthenticatedUser;
import com.crmplatform.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The workspace's subscription (any member can see it; owners/admins submit payment requests). */
@RestController
@RequestMapping("/api/v1/subscription")
@Tag(name = "Subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public SubscriptionResponse current() {
        return SubscriptionResponse.of(subscriptions.overview(workspace().workspaceId()));
    }

    @GetMapping("/plans")
    @PreAuthorize("isAuthenticated()")
    public List<PlanView> plans() {
        return subscriptions.activePlans().stream().map(PlanView::of).toList();
    }

    @GetMapping("/payment-methods")
    @PreAuthorize("isAuthenticated()")
    public List<MethodView> paymentMethods() {
        return subscriptions.activeMethods().stream().map(MethodView::of).toList();
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('workspace:manage')")
    public List<RequestView> requests() {
        return subscriptions.requestsOf(workspace().workspaceId()).stream().map(RequestView::of).toList();
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAuthority('workspace:manage')")
    public ResponseEntity<RequestView> submit(@Valid @RequestBody SubmitRequest body) {
        AuthenticatedUser user = workspace();
        SubscriptionRequest r = subscriptions.submit(user.workspaceId(), user.userId(), body.toData());
        return ResponseEntity.created(URI.create("/api/v1/subscription/requests/" + r.getId())).body(RequestView.of(r));
    }

    @PostMapping("/requests/{id}/cancel")
    @PreAuthorize("hasAuthority('workspace:manage')")
    public RequestView cancel(@PathVariable UUID id) {
        AuthenticatedUser user = workspace();
        return RequestView.of(subscriptions.cancel(user.workspaceId(), user.userId(), id));
    }

    private static AuthenticatedUser workspace() {
        AuthenticatedUser user = CurrentUser.require();
        if (!user.isMember()) {
            throw com.crmplatform.shared.error.ApiException.notFound("Workspace");
        }
        return user;
    }

    static String money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ------------------------------------------------------------------ DTOs

    public record SubmitRequest(@NotNull UUID planId, @NotBlank String billingPeriod, @NotNull UUID paymentMethodId,
            @NotBlank @Size(max = 100) String payerReference, @NotNull LocalDate paidOn, @NotNull UUID receiptFileId,
            @Size(max = 1000) String note) {
        RequestData toData() {
            return new RequestData(planId, billingPeriod, paymentMethodId, payerReference, paidOn, receiptFileId, note);
        }
    }

    public record Limits(Integer maxUsers, Integer maxActiveProjects, Integer maxJobsPerMonth, Integer maxStorageMb) {
        static Limits of(Plan p) {
            return new Limits(p.getMaxUsers(), p.getMaxActiveProjects(), p.getMaxJobsPerMonth(), p.getMaxStorageMb());
        }
    }

    public record PlanView(UUID id, String code, String nameAr, String nameEn, String descriptionAr,
            String descriptionEn, String priceMonthly, String priceYearly, String currency, Limits limits) {
        static PlanView of(Plan p) {
            return new PlanView(p.getId(), p.getCode(), p.getNameAr(), p.getNameEn(), p.getDescriptionAr(),
                    p.getDescriptionEn(), money(p.getPriceMonthly()), money(p.getPriceYearly()), p.getCurrency(),
                    Limits.of(p));
        }
    }

    public record MethodView(UUID id, String kind, String labelAr, String labelEn, String account,
            String instructionsAr, String instructionsEn) {
        static MethodView of(PaymentMethod m) {
            return new MethodView(m.getId(), m.getKind().name(), m.getLabelAr(), m.getLabelEn(), m.getAccount(),
                    m.getInstructionsAr(), m.getInstructionsEn());
        }
    }

    public record RequestView(UUID id, UUID planId, String billingPeriod, String amount, String currency,
            UUID paymentMethodId, String payerReference, LocalDate paidOn, UUID receiptFileId, String note,
            String status, String reviewNote, Instant reviewedAt, Instant createdAt) {
        static RequestView of(SubscriptionRequest r) {
            return new RequestView(r.getId(), r.getPlanId(), r.getBillingPeriod().name(), money(r.getAmount()),
                    r.getCurrency(), r.getPaymentMethodId(), r.getPayerReference(), r.getPaidOn(),
                    r.getReceiptFileId(), r.getNote(), r.getStatus().name(), r.getReviewNote(), r.getReviewedAt(),
                    r.getCreatedAt());
        }
    }

    public record Usage(long users, long activeProjects, long jobsThisMonth, long storageMb) {
    }

    public record SubscriptionResponse(String status, boolean readOnly, PlanView plan, String billingPeriod,
            Instant currentPeriodEnd, Instant graceEndsAt, long daysLeft, Usage usage, RequestView pendingRequest) {
        static SubscriptionResponse of(Overview o) {
            var u = o.usage();
            return new SubscriptionResponse(o.status().name(), o.readOnly(), PlanView.of(o.plan()),
                    o.subscription().getBillingPeriod() == null ? null : o.subscription().getBillingPeriod().name(),
                    o.subscription().getCurrentPeriodEnd(), o.graceEndsAt(), o.daysLeft(),
                    new Usage(u.users(), u.activeProjects(), u.jobsThisMonth(),
                            (u.storageBytes() + 1024 * 1024 - 1) / (1024 * 1024)),
                    o.pendingRequest() == null ? null : RequestView.of(o.pendingRequest()));
        }
    }
}

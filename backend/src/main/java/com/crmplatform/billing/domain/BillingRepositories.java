package com.crmplatform.billing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Platform-level repositories: no tenant filter, so every tenant-facing query names the workspace. */
public final class BillingRepositories {

    private BillingRepositories() {
    }

    public interface PlanRepository extends JpaRepository<Plan, UUID> {

        Optional<Plan> findByCode(String code);

        boolean existsByCode(String code);

        List<Plan> findByActiveTrueOrderBySortOrderAsc();

        List<Plan> findAllByOrderBySortOrderAsc();
    }

    public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

        List<PaymentMethod> findByActiveTrueOrderBySortOrderAsc();

        List<PaymentMethod> findAllByOrderBySortOrderAsc();
    }

    public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

        Optional<Subscription> findByWorkspaceId(UUID workspaceId);

        /** Candidates for the daily run: periods that ended or end soon, plus anything still in grace. */
        List<Subscription> findByCurrentPeriodEndBefore(Instant cutoff);
    }

    public interface SubscriptionRequestRepository extends JpaRepository<SubscriptionRequest, UUID> {

        List<SubscriptionRequest> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

        Optional<SubscriptionRequest> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

        boolean existsByWorkspaceIdAndStatus(UUID workspaceId, SubscriptionRequest.Status status);

        Page<SubscriptionRequest> findByStatus(SubscriptionRequest.Status status, Pageable pageable);
    }

    public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, UUID> {

        List<SubscriptionEvent> findByWorkspaceIdOrderByOccurredAtDesc(UUID workspaceId);
    }
}

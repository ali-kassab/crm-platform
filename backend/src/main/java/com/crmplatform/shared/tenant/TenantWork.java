package com.crmplatform.shared.tenant;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs work in a workspace scope inside its <em>own</em> transaction, opened after the scope is set.
 *
 * <p>Hibernate fixes the tenant when the session opens. A listener annotated {@code @Transactional}
 * (or Modulith's {@code @ApplicationModuleListener}) opens that session before its body can call
 * {@link TenantContext#runAs}, so it would run with whatever context happened to be on the thread
 * (none at all when events are republished at startup). Event listeners and background jobs use
 * this helper instead.
 */
@Component
public class TenantWork {

    private final TransactionTemplate requiresNew;

    public TenantWork(PlatformTransactionManager transactionManager) {
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void run(UUID workspaceId, UUID userId, Runnable work) {
        call(workspaceId, userId, () -> {
            work.run();
            return null;
        });
    }

    public <T> T call(UUID workspaceId, UUID userId, Supplier<T> work) {
        if (workspaceId == null || TenantContext.NONE.equals(workspaceId)) {
            throw new IllegalArgumentException("A workspace is required");
        }
        return TenantContext.runAs(workspaceId, userId, () -> requiresNew.execute(status -> work.get()));
    }

    /** Platform-level work across all workspaces (schedulers only). */
    public <T> T callAsSystem(Supplier<T> work) {
        return TenantContext.runAs(TenantContext.SYSTEM, null, () -> requiresNew.execute(status -> work.get()));
    }
}

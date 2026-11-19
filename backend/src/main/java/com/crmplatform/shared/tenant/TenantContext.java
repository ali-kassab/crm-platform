package com.crmplatform.shared.tenant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Holds the active workspace (tenant) and acting user for the current thread.
 *
 * <p>Set from the validated access token for HTTP requests, and explicitly by background jobs via
 * {@link #runAs(UUID, UUID, Supplier)}. The workspace is never taken from request payloads
 * (ADR-0004).
 */
public final class TenantContext {

    /**
     * Sentinel tenant that bypasses tenant filtering. Only platform-level jobs may use it, and only
     * explicitly via {@code runAs(SYSTEM, …)}; it is never the default.
     */
    public static final UUID SYSTEM = new UUID(0L, 0L);

    /**
     * Tenant used when no context is set. It matches no row, so a code path that forgot to set a
     * tenant sees nothing instead of everything (fail-closed), and inserts are rejected by
     * {@code TenantEntity}.
     */
    public static final UUID NONE = new UUID(-1L, -1L);

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> workspaceId() {
        Scope scope = CURRENT.get();
        return scope == null ? Optional.empty() : Optional.of(scope.workspaceId());
    }

    public static UUID requireWorkspaceId() {
        return workspaceId().orElseThrow(() -> new IllegalStateException("No active workspace in context"));
    }

    public static Optional<UUID> userId() {
        Scope scope = CURRENT.get();
        return scope == null ? Optional.empty() : Optional.ofNullable(scope.userId());
    }

    public static void set(UUID workspaceId, UUID userId) {
        CURRENT.set(new Scope(workspaceId, userId));
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs {@code work} with the given tenant and user, restoring the previous context afterwards. */
    public static <T> T runAs(UUID workspaceId, UUID userId, Supplier<T> work) {
        Scope previous = CURRENT.get();
        CURRENT.set(new Scope(workspaceId, userId));
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(UUID workspaceId, UUID userId, Runnable work) {
        runAs(workspaceId, userId, () -> {
            work.run();
            return null;
        });
    }

    private record Scope(UUID workspaceId, UUID userId) {
    }
}

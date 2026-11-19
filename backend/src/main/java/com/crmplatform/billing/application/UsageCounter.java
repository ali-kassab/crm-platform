package com.crmplatform.billing.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only usage counts for plan limits and the subscription page. Reads the owning modules'
 * tables directly (counts only, never writes) so limits work outside a tenant context too, e.g.
 * while accepting an invitation.
 */
@Component
public class UsageCounter {

    private final JdbcTemplate jdbc;

    public UsageCounter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long activeUsers(UUID workspaceId) {
        return count("select count(*) from memberships m join users u on u.id = m.user_id "
                + "where m.workspace_id = ? and m.active and u.active", workspaceId);
    }

    public long activeProjects(UUID workspaceId) {
        return count("select count(*) from projects where workspace_id = ? and deleted_at is null", workspaceId);
    }

    /** Jobs created since the start of the current calendar month (UTC). */
    public long jobsThisMonth(UUID workspaceId, Instant now) {
        Instant monthStart = now.atOffset(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        Long n = jdbc.queryForObject("select count(*) from jobs where workspace_id = ? and created_at >= ?",
                Long.class, workspaceId, java.sql.Timestamp.from(monthStart));
        return n == null ? 0 : n;
    }

    public long storageBytes(UUID workspaceId) {
        return count("select coalesce(sum(size_bytes), 0) from stored_files where workspace_id = ? "
                + "and deleted_at is null", workspaceId);
    }

    private long count(String sql, UUID workspaceId) {
        Long n = jdbc.queryForObject(sql, Long.class, workspaceId);
        return n == null ? 0 : n;
    }

    /** Active owners and admins (recipients of billing notices). */
    public java.util.List<UUID> managers(UUID workspaceId) {
        return jdbc.queryForList("select m.user_id from memberships m join users u on u.id = m.user_id "
                + "where m.workspace_id = ? and m.active and u.active and m.role in ('OWNER', 'ADMIN')",
                UUID.class, workspaceId);
    }

    public record Usage(long users, long activeProjects, long jobsThisMonth, long storageBytes) {
    }

    public Usage of(UUID workspaceId, Instant now) {
        return new Usage(activeUsers(workspaceId), activeProjects(workspaceId), jobsThisMonth(workspaceId, now),
                storageBytes(workspaceId));
    }
}

package com.crmplatform.identity.application;

import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.domain.UserRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.web.PageResponse;
import com.crmplatform.shared.web.Pagination;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-level user administration: who is registered, in which companies, and whether the
 * account may sign in. Only account metadata is exposed — never password hashes, tokens, phone
 * numbers or anything from a tenant's business data. Platform-admin accounts cannot be changed here
 * (they manage themselves through the password-checked account endpoints).
 */
@Service
public class PlatformUserService {

    /** API sort field → SQL expression (fixed whitelist; never user text). */
    private static final Map<String, String> SORT = Map.of(
            "createdAt", "u.created_at", "email", "u.email", "lastLoginAt", "u.last_login_at");

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final MembershipLookup lookup;
    private final AuditTrail audit;

    public PlatformUserService(NamedParameterJdbcTemplate jdbc, UserRepository users, RefreshTokenService refreshTokens,
            MembershipLookup lookup, AuditTrail audit) {
        this.jdbc = jdbc;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.lookup = lookup;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserRow> list(UserFilter filter, Integer page, Integer size, List<String> sort) {
        Pageable pageable = Pagination.pageable(page, size, sort, SORT, Sort.by(Sort.Direction.DESC, "u.created_at"));
        StringBuilder where = new StringBuilder(" where 1 = 1");
        MapSqlParameterSource args = new MapSqlParameterSource();
        String like = Pagination.likePattern(filter.q());
        if (like != null) {
            where.append(" and (app_search_norm(u.email) like :q or app_search_norm(u.first_name || ' ' || u.last_name) like :q)");
            args.addValue("q", like);
        }
        if (filter.status() != null) {
            switch (filter.status().toUpperCase(Locale.ROOT)) {
                case "ACTIVE" -> where.append(" and u.active");
                case "INACTIVE" -> where.append(" and not u.active");
                default -> throw ApiException.badRequest("VALIDATION_ERROR", "status must be ACTIVE or INACTIVE");
            }
        }
        if (filter.kind() != null) {
            switch (filter.kind().toUpperCase(Locale.ROOT)) {
                case "PLATFORM_ADMIN" -> where.append(" and u.super_admin");
                case "TENANT" -> where.append(" and not u.super_admin");
                default -> throw ApiException.badRequest("VALIDATION_ERROR", "kind must be PLATFORM_ADMIN or TENANT");
            }
        }
        if (filter.workspaceId() != null) {
            where.append(" and exists (select 1 from memberships m where m.user_id = u.id and m.workspace_id = :ws)");
            args.addValue("ws", filter.workspaceId());
        }
        Long total = jdbc.queryForObject("select count(*) from users u" + where, args, Long.class);
        String order = pageable.getSort().stream()
                .map(o -> o.getProperty() + (o.isAscending() ? " asc" : " desc") + " nulls last")
                .collect(Collectors.joining(", "));
        args.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<UserRow> rows = jdbc.query("""
                select u.id, u.email, u.first_name, u.last_name, u.active, u.super_admin, u.created_at, u.last_login_at
                from users u""" + where + " order by " + order + ", u.id limit :limit offset :offset", args,
                (rs, i) -> new UserRow(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("first_name"), rs.getString("last_name"), rs.getBoolean("active"),
                        rs.getBoolean("super_admin"), instant(rs, "created_at"), instant(rs, "last_login_at"),
                        new ArrayList<>()));
        attachCompanies(rows);
        return PageResponse.of(new PageImpl<>(rows, pageable, total == null ? 0 : total));
    }

    private void attachCompanies(List<UserRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Map<UUID, UserRow> byId = new LinkedHashMap<>();
        rows.forEach(r -> byId.put(r.id(), r));
        jdbc.query("""
                select m.user_id, w.id, w.name, m.role, m.active
                from memberships m join workspaces w on w.id = m.workspace_id
                where m.user_id in (:ids) order by m.joined_at""", Map.of("ids", byId.keySet()), rs -> {
            byId.get(rs.getObject("user_id", UUID.class)).companies().add(new UserCompany(
                    rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("role"), rs.getBoolean("active")));
        });
    }

    /**
     * Blocks sign-in for a tenant account: refresh sessions are revoked and the cached role is dropped,
     * so already-issued access tokens lose their workspace on the next request. Data stays untouched.
     */
    @Transactional
    public UserRow setActive(UUID adminId, UUID userId, boolean active, String reason) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.isSuperAdmin()) {
            throw ApiException.conflict("PLATFORM_ADMIN_PROTECTED",
                    "Platform admin accounts cannot be activated or deactivated here");
        }
        if (!active && (reason == null || reason.isBlank())) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A reason is required");
        }
        if (user.isActive() != active) {
            user.setActive(active);
            users.saveAndFlush(user);
            if (!active) {
                refreshTokens.revokeAllForUser(userId);
            }
            jdbc.queryForList("select workspace_id from memberships where user_id = :id", Map.of("id", userId),
                    UUID.class).forEach(workspaceId -> lookup.evict(userId, workspaceId));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("email", user.getEmail());
            details.put("active", Map.of("from", !active, "to", active));
            if (reason != null && !reason.isBlank()) {
                details.put("reason", reason.trim());
            }
            audit.platform(active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", userId, adminId, null, details);
        }
        UserRow row = new UserRow(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.isActive(), false, user.getCreatedAt(), user.getLastLoginAt(), new ArrayList<>());
        attachCompanies(List.of(row));
        return row;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    public record UserFilter(String q, String status, String kind, UUID workspaceId) {
    }

    /** Account metadata only: no hash, token, phone or job title. */
    public record UserRow(UUID id, String email, String firstName, String lastName, boolean active,
            boolean platformAdmin, Instant createdAt, Instant lastLoginAt, List<UserCompany> companies) {
    }

    public record UserCompany(UUID id, String name, String role, boolean active) {
    }
}

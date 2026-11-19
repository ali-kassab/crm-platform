package com.crmplatform.identity.application;

import com.crmplatform.identity.domain.Membership;
import com.crmplatform.identity.domain.MembershipRepository;
import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.domain.UserRepository;
import com.crmplatform.identity.domain.Workspace;
import com.crmplatform.identity.domain.WorkspaceRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.i18n.Languages;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Workspace settings, profile and platform statistics (FR-IAM-08, 12, 13). */
@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    public WorkspaceService(WorkspaceRepository workspaces, MembershipRepository memberships, UserRepository users,
            RefreshTokenService refreshTokens, Clock clock) {
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Workspace get(UUID workspaceId) {
        return workspaces.findById(workspaceId).orElseThrow(() -> ApiException.notFound("Workspace"));
    }

    @Transactional
    public Workspace rename(UUID workspaceId, String name) {
        Workspace workspace = get(workspaceId);
        workspace.rename(name);
        return workspace;
    }

    /**
     * Updates name and regional settings; only the fields present in {@code fields} change.
     * Language, locale, currency and time zone are independent (D11).
     */
    @Transactional
    public Workspace updateSettings(UUID workspaceId, WorkspaceSettings s, Set<String> fields) {
        Workspace w = get(workspaceId);
        if (fields.contains("name")) {
            if (s.name() == null || s.name().trim().length() < 2) {
                throw ApiException.badRequest("VALIDATION_ERROR", "name is required");
            }
            w.rename(s.name());
        }
        String language = fields.contains("defaultLanguage") ? requireLanguage(s.defaultLanguage())
                : w.getDefaultLanguage();
        String locale = fields.contains("locale") ? requireLocale(s.locale()) : w.getLocale();
        String currency = fields.contains("currency") ? requireCurrency(s.currency()) : w.getCurrency();
        String timeZone = fields.contains("timeZone") ? requireTimeZone(s.timeZone()) : w.getTimeZone();
        Workspace.Sector sector = fields.contains("sector") ? parseSector(s.sector()) : w.getSector();
        w.configure(language, locale, currency, timeZone, sector);
        return w;
    }

    /** {@code null} clears the preference (the workspace default applies). */
    @Transactional
    public User setPreferredLanguage(UUID userId, String language) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.setPreferredLanguage(language == null ? null : requireLanguage(language));
        return user;
    }

    static String requireLanguage(String value) {
        if (!Languages.isSupported(value)) {
            throw ApiException.badRequest("UNSUPPORTED_LANGUAGE", "Supported languages are ar and en");
        }
        return value;
    }

    static String requireLocale(String value) {
        if (value == null || !value.matches("^[a-z]{2,3}(-[A-Z]{2})?$")) {
            throw ApiException.badRequest("INVALID_LOCALE", "locale must look like ar-EG or en-US");
        }
        return value;
    }

    static String requireCurrency(String value) {
        try {
            return Currency.getInstance(value == null ? "" : value).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_CURRENCY", "currency must be an ISO 4217 code such as EGP");
        }
    }

    static String requireTimeZone(String value) {
        try {
            return ZoneId.of(value == null ? "" : value).getId();
        } catch (DateTimeException e) {
            throw ApiException.badRequest("INVALID_TIME_ZONE", "timeZone must be an IANA zone such as Africa/Cairo");
        }
    }

    static Workspace.Sector parseSector(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Workspace.Sector.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_SECTOR", "sector must be FIT_OUT, SECURITY_SYSTEMS or MAINTENANCE");
        }
    }

    public record WorkspaceSettings(String name, String defaultLanguage, String locale, String currency,
            String timeZone, String sector) {
    }

    @Transactional
    public void delete(UUID workspaceId, String confirmName) {
        Workspace workspace = get(workspaceId);
        if (confirmName == null || !confirmName.trim().equals(workspace.getName())) {
            throw ApiException.badRequest("CONFIRMATION_MISMATCH", "Type the workspace name exactly to confirm");
        }
        Instant now = Instant.now(clock);
        workspace.delete(now);
        memberships.findByWorkspaceId(workspaceId, org.springframework.data.domain.Pageable.unpaged())
                .forEach(m -> {
                    m.setActive(false);
                    refreshTokens.revokeAllForMembership(m.getUserId(), workspaceId);
                });
    }

    @Transactional(readOnly = true)
    public Profile profile(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        List<Membership> active = memberships.findByUserIdAndActiveTrue(userId);
        Map<UUID, Workspace> byId = workspaces.findAllById(active.stream().map(Membership::getWorkspaceId).toList())
                .stream().collect(Collectors.toMap(Workspace::getId, Function.identity()));
        List<Profile.WorkspaceMembership> list = active.stream()
                .filter(m -> byId.containsKey(m.getWorkspaceId()))
                .map(m -> new Profile.WorkspaceMembership(byId.get(m.getWorkspaceId()), m.getRole().name()))
                .toList();
        return new Profile(user, list);
    }

    @Transactional
    public User updateProfile(UUID userId, String firstName, String lastName, String jobTitle, String phone,
            UUID avatarFileId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.updateProfile(firstName, lastName, jobTitle, phone, avatarFileId);
        return user;
    }

    @Transactional(readOnly = true)
    public PlatformStats platformStats() {
        return new PlatformStats(workspaces.count(), users.count(), memberships.count());
    }

    public record Profile(User user, List<WorkspaceMembership> workspaces) {
        public record WorkspaceMembership(Workspace workspace, String role) {
        }
    }

    public record PlatformStats(long workspaces, long users, long memberships) {
    }
}

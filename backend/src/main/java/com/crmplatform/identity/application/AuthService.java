package com.crmplatform.identity.application;

import com.crmplatform.identity.WorkspaceCreated;
import com.crmplatform.identity.domain.Membership;
import com.crmplatform.identity.domain.MembershipRepository;
import com.crmplatform.identity.domain.PasswordResetToken;
import com.crmplatform.identity.domain.PasswordResetTokenRepository;
import com.crmplatform.identity.domain.Role;
import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.domain.UserRepository;
import com.crmplatform.identity.domain.Workspace;
import com.crmplatform.identity.domain.WorkspaceRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.mail.EmailSender;
import com.crmplatform.shared.mail.EmailTemplates;
import com.crmplatform.shared.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Authentication flows F1–F7 (AUTH_FLOWS.md). */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final WorkspaceRepository workspaces;
    private final MembershipRepository memberships;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService loginAttempts;
    private final TokenDenylist denylist;
    private final RateLimiter rateLimiter;
    private final MembershipLookup membershipLookup;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final ApplicationEventPublisher events;
    private final IdentityProperties properties;
    private final Clock clock;
    private final String clientBaseUrl;
    private final String dummyHash;

    public AuthService(UserRepository users, WorkspaceRepository workspaces, MembershipRepository memberships,
            PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            AccessTokenService accessTokens, RefreshTokenService refreshTokens, LoginAttemptService loginAttempts,
            TokenDenylist denylist, RateLimiter rateLimiter, MembershipLookup membershipLookup, EmailSender emailSender,
            EmailTemplates templates, ApplicationEventPublisher events, IdentityProperties properties, Clock clock,
            @Value("${app.client-base-url}") String clientBaseUrl) {
        this.users = users;
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.denylist = denylist;
        this.rateLimiter = rateLimiter;
        this.membershipLookup = membershipLookup;
        this.emailSender = emailSender;
        this.templates = templates;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
        this.clientBaseUrl = clientBaseUrl;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-" + UUID.randomUUID());
    }

    // ---------------------------------------------------------------- F1 register

    @Transactional
    public AuthSession register(RegisterCommand cmd, ClientInfo client) {
        rateLimiter.check("register:" + client.ip(), 5, Duration.ofHours(1));
        String email = User.normalizeEmail(cmd.email());
        passwordPolicy.validate(cmd.password(), email);
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = users.save(new User(email, passwordEncoder.encode(cmd.password()), cmd.firstName(), cmd.lastName()));
        Workspace workspace = workspaces.save(new Workspace(cmd.workspaceName(), uniqueSlug(cmd.workspaceName())));
        Instant now = Instant.now(clock);
        memberships.save(new Membership(workspace.getId(), user.getId(), Role.OWNER, now));
        user.recordLogin(now, workspace.getId());
        events.publishEvent(new WorkspaceCreated(workspace.getId(), user.getId()));
        log.info("Workspace {} registered", workspace.getId());
        return openSession(user, workspace, Role.OWNER, client);
    }

    // ---------------------------------------------------------------- F2 login

    @Transactional(noRollbackFor = ApiException.class)
    public AuthSession login(String rawEmail, String password, UUID requestedWorkspace, ClientInfo client) {
        String email = User.normalizeEmail(rawEmail);
        rateLimiter.check("login-ip:" + client.ip(), 10, Duration.ofMinutes(1));
        rateLimiter.check("login-email:" + email, 5, Duration.ofMinutes(1));
        loginAttempts.ensureNotLocked(email);

        User user = users.findByEmail(email).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean matches = passwordEncoder.matches(password == null ? "" : password, hash);
        if (user == null || !matches || !user.isActive()) {
            loginAttempts.recordFailure(email);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
        loginAttempts.reset(email);

        List<Membership> active = memberships.findByUserIdAndActiveTrue(user.getId());
        if (active.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_WORKSPACE", "You are not a member of any workspace");
        }
        Membership chosen = active.stream()
                .filter(m -> m.getWorkspaceId().equals(requestedWorkspace))
                .findFirst()
                .or(() -> active.stream().filter(m -> m.getWorkspaceId().equals(user.getLastWorkspaceId())).findFirst())
                .orElseGet(() -> active.stream().max(Comparator.comparing(Membership::getJoinedAt)).orElseThrow());
        Workspace workspace = workspaces.findById(chosen.getWorkspaceId()).orElseThrow(() ->
                new ApiException(HttpStatus.FORBIDDEN, "NO_WORKSPACE", "You are not a member of any workspace"));
        user.recordLogin(Instant.now(clock), workspace.getId());
        return openSession(user, workspace, chosen.getRole(), client);
    }

    // ---------------------------------------------------------------- F3 refresh

    @Transactional(noRollbackFor = ApiException.class)
    public AuthSession refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("INVALID_REFRESH", "Session expired. Please sign in again.");
        }
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken, client.userAgent(), client.ip());
        User user = users.findById(rotation.userId()).filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH", "Session expired. Please sign in again."));
        Membership membership = memberships.findByWorkspaceIdAndUserId(rotation.workspaceId(), user.getId())
                .filter(Membership::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NO_WORKSPACE",
                        "You are no longer a member of this workspace"));
        Workspace workspace = workspaces.findById(rotation.workspaceId())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH", "Session expired. Please sign in again."));
        var access = accessTokens.issue(user.getId(), workspace.getId(), membership.getRole(), user.isSuperAdmin());
        return new AuthSession(access.value(), access.expiresInSeconds(), rotation.next().value(),
                rotation.next().expiresAt(), user, workspace, membership.getRole(), membership.getRole().permissions());
    }

    // ---------------------------------------------------------------- F4 switch workspace

    @Transactional
    public AuthSession switchWorkspace(UUID userId, UUID workspaceId, ClientInfo client) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        Membership membership = memberships.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(Membership::isActive)
                .orElseThrow(() -> ApiException.notFound("Workspace"));
        Workspace workspace = workspaces.findById(workspaceId).orElseThrow(() -> ApiException.notFound("Workspace"));
        user.recordLogin(Instant.now(clock), workspaceId);
        return openSession(user, workspace, membership.getRole(), client);
    }

    // ---------------------------------------------------------------- F5 logout

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revokeFamilyOf(rawRefreshToken);
        }
    }

    @Transactional
    public void logoutAll(UUID userId, String accessTokenId, Instant accessTokenExpiresAt) {
        refreshTokens.revokeAllForUser(userId);
        denylist.deny(accessTokenId, accessTokenExpiresAt);
    }

    // ---------------------------------------------------------------- F6 forgot / reset

    @Transactional
    public void requestPasswordReset(String rawEmail, ClientInfo client) {
        String email = User.normalizeEmail(rawEmail);
        rateLimiter.check("forgot-ip:" + client.ip(), 10, Duration.ofHours(1));
        try {
            rateLimiter.check("forgot-email:" + email, 3, Duration.ofHours(1));
        } catch (ApiException e) {
            return; // stay silent: never reveal whether the account exists
        }
        users.findByEmail(email).filter(User::isActive).ifPresent(user -> {
            Instant now = Instant.now(clock);
            resetTokens.invalidateAll(user.getId(), now);
            String raw = SecureTokens.newToken();
            resetTokens.save(new PasswordResetToken(user.getId(), SecureTokens.hash(raw),
                    now.plus(properties.passwordResetTtl())));
            String html = templates.render("password-reset", Map.of(
                    "firstName", user.getFirstName(),
                    "resetUrl", clientBaseUrl + "/reset-password?token=" + raw,
                    "expiresInMinutes", properties.passwordResetTtl().toMinutes()));
            afterCommit(() -> emailSender.send(new EmailSender.EmailMessage(List.of(user.getEmail()),
                    "Reset your CRM Platform password", html, null)));
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now(clock);
        PasswordResetToken token = resetTokens.findByTokenHash(SecureTokens.hash(rawToken == null ? "" : rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "This reset link is invalid or has expired"));
        User user = users.findById(token.getUserId()).orElseThrow(() -> ApiException.notFound("User"));
        passwordPolicy.validate(newPassword, user.getEmail());
        user.changePassword(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        refreshTokens.revokeAllForUser(user.getId());
        loginAttempts.reset(user.getEmail());
    }

    // ---------------------------------------------------------------- F7 change password

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, String currentRefreshToken) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Current password is incorrect");
        }
        passwordPolicy.validate(newPassword, user.getEmail());
        user.changePassword(passwordEncoder.encode(newPassword));
        UUID keep = currentRefreshToken == null ? null : refreshTokens.familyOf(currentRefreshToken);
        refreshTokens.revokeAllForUserExcept(userId, keep);
    }

    // ---------------------------------------------------------------- helpers

    AuthSession openSession(User user, Workspace workspace, Role role, ClientInfo client) {
        membershipLookup.evict(user.getId(), workspace.getId());
        var access = accessTokens.issue(user.getId(), workspace.getId(), role, user.isSuperAdmin());
        var refresh = refreshTokens.issueNewFamily(user.getId(), workspace.getId(), client.userAgent(), client.ip());
        return new AuthSession(access.value(), access.expiresInSeconds(), refresh.value(), refresh.expiresAt(), user,
                workspace, role, role.permissions());
    }

    private String uniqueSlug(String name) {
        String base = Workspace.slugify(name);
        String slug = base;
        int attempt = 1;
        while (workspaces.slugTaken(slug)) {
            slug = base + "-" + (++attempt);
        }
        return slug;
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    public record RegisterCommand(String email, String password, String firstName, String lastName,
            String workspaceName) {
    }

    public record ClientInfo(String ip, String userAgent) {
    }
}

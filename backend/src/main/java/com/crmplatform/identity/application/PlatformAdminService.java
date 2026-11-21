package com.crmplatform.identity.application;

import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.domain.UserRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.events.AuditTrail;
import com.crmplatform.shared.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-admin sign-in and account changes. Admin sessions are short-lived access tokens scoped to
 * no workspace (see {@link AccessTokenService#issuePlatform}); every sensitive change is audited at
 * platform level and requires the current password.
 */
@Service
public class PlatformAdminService {

    static final int MIN_ADMIN_PASSWORD_LENGTH = 14;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService loginAttempts;
    private final RateLimiter rateLimiter;
    private final PlatformAdminProperties properties;
    private final AuditTrail audit;
    private final String dummyHash;

    public PlatformAdminService(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            AccessTokenService accessTokens, RefreshTokenService refreshTokens, LoginAttemptService loginAttempts,
            RateLimiter rateLimiter, PlatformAdminProperties properties, AuditTrail audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-" + UUID.randomUUID());
    }

    /**
     * Same lockout and rate limits as normal sign-in; a non-admin gets the same generic error. Failed
     * attempts against an admin account are audited (no rollback of that audit row).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public PlatformSession login(String rawEmail, String password, String clientIp) {
        String email = User.normalizeEmail(rawEmail);
        rateLimiter.check("admin-login-ip:" + clientIp, 5, Duration.ofMinutes(1));
        rateLimiter.check("login-email:" + email, 5, Duration.ofMinutes(1));
        loginAttempts.ensureNotLocked(email);
        User user = users.findByEmail(email).orElse(null);
        boolean matches = passwordEncoder.matches(password == null ? "" : password,
                user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches || !user.isActive() || !user.isSuperAdmin()) {
            loginAttempts.recordFailure(email);
            if (user != null && user.isSuperAdmin()) {
                audit.platform("ADMIN_LOGIN_FAILED", "PLATFORM_ADMIN", user.getId(), null, null, Map.of("ip", clientIp));
            }
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
        loginAttempts.reset(email);
        var token = accessTokens.issuePlatform(user.getId(), properties.sessionTtl(),
                user.effectiveLanguage(null));
        audit.platform("ADMIN_LOGIN", "PLATFORM_ADMIN", user.getId(), user.getId(), null, Map.of("ip", clientIp));
        return new PlatformSession(token.value(), token.expiresInSeconds(), user);
    }

    @Transactional(readOnly = true)
    public User account(UUID adminId) {
        return requireAdmin(adminId);
    }

    @Transactional
    public User changeEmail(UUID adminId, String currentPassword, String newEmail) {
        User admin = requireAdmin(adminId);
        verifyPassword(admin, currentPassword);
        String email = User.normalizeEmail(newEmail);
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A valid email is required");
        }
        if (email.equals(admin.getEmail())) {
            return admin;
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "This email is already in use");
        }
        String previous = admin.getEmail();
        admin.changeEmail(email);
        users.saveAndFlush(admin);
        audit.platform("ADMIN_EMAIL_CHANGED", "PLATFORM_ADMIN", adminId, adminId, null,
                Map.of("from", previous, "to", email));
        return admin;
    }

    @Transactional
    public void changePassword(UUID adminId, String currentPassword, String newPassword) {
        User admin = requireAdmin(adminId);
        verifyPassword(admin, currentPassword);
        validateAdminPassword(newPassword, admin.getEmail());
        admin.changePassword(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUserExcept(adminId, null); // any workspace sessions of this account
        audit.platform("ADMIN_PASSWORD_CHANGED", "PLATFORM_ADMIN", adminId, adminId, null, Map.of());
    }

    /** The admin's display name (the sign-in email and password have their own, password-checked flows). */
    @Transactional
    public User updateName(UUID adminId, String firstName, String lastName) {
        User admin = requireAdmin(adminId);
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        if (first.isEmpty() || last.isEmpty() || first.length() > 60 || last.length() > 60) {
            throw ApiException.badRequest("VALIDATION_ERROR", "First and last name are required (at most 60 characters)");
        }
        Map<String, Object> before = Map.of("firstName", admin.getFirstName(), "lastName", admin.getLastName());
        admin.updateProfile(first, last, admin.getJobTitle(), admin.getPhone(), admin.getAvatarFileId());
        users.saveAndFlush(admin);
        Map<String, Object> changes = AuditTrail.diff(before, Map.of("firstName", first, "lastName", last));
        if (!changes.isEmpty()) {
            audit.platform("ADMIN_PROFILE_UPDATED", "PLATFORM_ADMIN", adminId, adminId, null, changes);
        }
        return admin;
    }

    /** Called by the e-mailed reset flow for admin accounts: stronger rule plus a platform audit row. */
    void onPasswordReset(User admin, String newPassword) {
        validateAdminPassword(newPassword, admin.getEmail());
        audit.platform("ADMIN_PASSWORD_RESET", "PLATFORM_ADMIN", admin.getId(), admin.getId(), "reset-link", Map.of());
    }

    void validateAdminPassword(String password, String email) {
        if (password == null || password.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("PASSWORD_TOO_SHORT",
                    "Platform admin passwords must be at least " + MIN_ADMIN_PASSWORD_LENGTH + " characters long");
        }
        passwordPolicy.validate(password, email);
    }

    private User requireAdmin(UUID adminId) {
        return users.findById(adminId).filter(User::isSuperAdmin).filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Platform admins only"));
    }

    private void verifyPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Current password is incorrect");
        }
    }

    public record PlatformSession(String accessToken, long expiresIn, User user) {
    }
}

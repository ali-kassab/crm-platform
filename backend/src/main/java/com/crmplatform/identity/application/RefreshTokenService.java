package com.crmplatform.identity.application;

import com.crmplatform.identity.domain.RefreshToken;
import com.crmplatform.identity.domain.RefreshTokenRepository;
import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.id.UuidV7;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token lifecycle: issue, rotate (single use) and detect reuse (ADR-0003, AUTH_FLOWS F3,
 * EC-02 grace window).
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final IdentityProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, IdentityProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new family. */
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedRefreshToken issueNewFamily(UUID userId, UUID workspaceId, String userAgent, String ip) {
        Instant now = Instant.now(clock);
        return issue(userId, workspaceId, UuidV7.generate(), now.plus(properties.refreshTokenAbsoluteTtl()),
                userAgent, ip);
    }

    /**
     * Rotates a presented token. Returns the successor; a token already rotated outside the grace
     * window is treated as theft and the whole family is revoked.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken, String userAgent, String ip) {
        Instant now = Instant.now(clock);
        RefreshToken current = repository.findByTokenHashForUpdate(SecureTokens.hash(rawToken))
                .orElseThrow(RefreshTokenService::invalid);

        if (current.getRevokedAt() != null || !current.getAbsoluteExpiresAt().isAfter(now)) {
            throw invalid();
        }
        if (current.getRotatedAt() != null) {
            boolean withinGrace = current.getRotatedAt().plus(properties.refreshReuseGrace()).isAfter(now);
            if (!withinGrace) {
                repository.revokeFamily(current.getFamilyId(), now);
                log.warn("Refresh token reuse detected; family {} revoked", current.getFamilyId());
                throw ApiException.unauthorized("REFRESH_REUSED", "Session expired. Please sign in again.");
            }
            // Concurrent refresh from another tab: issue a sibling in the same family (EC-02).
        } else if (!current.isActive(now)) {
            throw invalid();
        }
        IssuedRefreshToken next = issue(current.getUserId(), current.getWorkspaceId(), current.getFamilyId(),
                current.getAbsoluteExpiresAt(), userAgent, ip);
        if (current.getRotatedAt() == null) {
            current.rotate(now, next.id());
        }
        return new Rotation(current.getUserId(), current.getWorkspaceId(), current.getFamilyId(), next);
    }

    /** Revokes the family of the presented token; unknown tokens are ignored (idempotent logout). */
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        repository.findByTokenHashForUpdate(SecureTokens.hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), Instant.now(clock)));
    }

    @Transactional
    public UUID familyOf(String rawToken) {
        return repository.findByTokenHashForUpdate(SecureTokens.hash(rawToken)).map(RefreshToken::getFamilyId)
                .orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAllForUserExcept(UUID userId, UUID keepFamily) {
        if (keepFamily == null) {
            revokeAllForUser(userId);
        } else {
            repository.revokeAllForUserExcept(userId, keepFamily, Instant.now(clock));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAllForMembership(UUID userId, UUID workspaceId) {
        repository.revokeAllForMembership(userId, workspaceId, Instant.now(clock));
    }

    private IssuedRefreshToken issue(UUID userId, UUID workspaceId, UUID familyId, Instant absoluteExpiry,
            String userAgent, String ip) {
        Instant now = Instant.now(clock);
        String raw = SecureTokens.newToken();
        RefreshToken token = new RefreshToken(userId, workspaceId, familyId, SecureTokens.hash(raw),
                now.plus(properties.refreshTokenTtl()), absoluteExpiry, userAgent, ip);
        repository.save(token);
        Instant expiresAt = now.plus(properties.refreshTokenTtl()).isAfter(absoluteExpiry)
                ? absoluteExpiry : now.plus(properties.refreshTokenTtl());
        return new IssuedRefreshToken(token.getId(), raw, expiresAt);
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("INVALID_REFRESH", "Session expired. Please sign in again.");
    }

    public record IssuedRefreshToken(UUID id, String value, Instant expiresAt) {
    }

    public record Rotation(UUID userId, UUID workspaceId, UUID familyId, IssuedRefreshToken next) {
    }
}

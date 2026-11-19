package com.crmplatform.identity.domain;

import com.crmplatform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One link of a refresh-token rotation chain ("family"). Only the SHA-256 hash of the opaque token
 * is stored (ADR-0003).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(length = 45)
    private String ip;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, UUID workspaceId, UUID familyId, String tokenHash, Instant expiresAt,
            Instant absoluteExpiresAt, String userAgent, String ip) {
        this.userId = userId;
        this.workspaceId = workspaceId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt.isAfter(absoluteExpiresAt) ? absoluteExpiresAt : expiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length()));
        this.ip = ip;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && rotatedAt == null && expiresAt.isAfter(now);
    }

    public void rotate(Instant at, UUID replacedBy) {
        this.rotatedAt = at;
        this.replacedById = replacedBy;
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            this.revokedAt = at;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}

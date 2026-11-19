package com.crmplatform.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Revoked access-token ids ({@code jti}) kept until the token would expire anyway. Used by
 * logout-all so already-issued access tokens stop working immediately (ADR-0003).
 */
@Service
public class TokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(TokenDenylist.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public TokenDenylist(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void deny(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(clock), expiresAt);
        if (jti == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set("jti-deny:" + jti, "1", ttl);
        } catch (RuntimeException e) {
            log.warn("Could not denylist token {}: {}", jti, e.getMessage());
        }
    }

    public boolean isDenied(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey("jti-deny:" + jti));
        } catch (RuntimeException e) {
            log.warn("Token denylist unavailable: {}", e.getMessage());
            return false;
        }
    }
}

package com.crmplatform.identity.application;

import com.crmplatform.shared.error.ApiException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Account lockout after repeated failed logins (5 failures → 15 minutes). Uses Redis so the lock
 * is shared across instances; falls back to a per-instance counter if Redis is down (EC-41).
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final StringRedisTemplate redis;
    private final IdentityProperties properties;
    private final Map<String, Integer> localFallback = new ConcurrentHashMap<>();

    public LoginAttemptService(StringRedisTemplate redis, IdentityProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void ensureNotLocked(String email) {
        String key = key(email);
        try {
            String value = redis.opsForValue().get(key);
            if (value != null && Integer.parseInt(value) >= properties.maxFailedLogins()) {
                Long ttl = redis.getExpire(key);
                throw locked(ttl == null || ttl < 0 ? properties.lockoutDuration().toSeconds() : ttl);
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Lockout store unavailable, using local fallback: {}", e.getMessage());
            if (localFallback.getOrDefault(key, 0) >= properties.maxFailedLogins()) {
                throw locked(properties.lockoutDuration().toSeconds());
            }
        }
    }

    public void recordFailure(String email) {
        String key = key(email);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, properties.lockoutDuration());
            } else if (count != null && count >= properties.maxFailedLogins()) {
                redis.expire(key, properties.lockoutDuration());
            }
        } catch (RuntimeException e) {
            localFallback.merge(key, 1, Integer::sum);
        }
    }

    public void reset(String email) {
        String key = key(email);
        localFallback.remove(key);
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not reset login attempts: {}", e.getMessage());
        }
    }

    private static String key(String email) {
        return "login-fail:" + email;
    }

    private static ApiException locked(long retryAfterSeconds) {
        return new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "Too many failed sign-in attempts. Try again later.", Map.of("retryAfter", retryAfterSeconds));
    }

    Duration lockoutDuration() {
        return properties.lockoutDuration();
    }
}

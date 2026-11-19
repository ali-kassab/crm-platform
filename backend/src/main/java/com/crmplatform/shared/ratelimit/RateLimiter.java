package com.crmplatform.shared.ratelimit;

import com.crmplatform.shared.error.ApiException;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiter on Redis ({@code INCR} + {@code EXPIRE}). Fails open when Redis is
 * unavailable so an outage never locks users out (ADR-0010).
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Counts one hit for {@code key}; throws 429 RATE_LIMITED once {@code limit} is exceeded. */
    public void check(String key, int limit, Duration window) {
        String redisKey = "rl:" + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, window);
            }
            if (count != null && count > limit) {
                Long ttl = redis.getExpire(redisKey);
                long retryAfter = ttl == null || ttl < 0 ? window.toSeconds() : ttl;
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "Too many requests. Please try again later.", Map.of("retryAfter", retryAfter));
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request for key {}: {}", key, e.getMessage());
        }
    }
}

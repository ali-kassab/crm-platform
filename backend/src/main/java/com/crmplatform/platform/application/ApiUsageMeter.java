package com.crmplatform.platform.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Counts authenticated API requests per workspace and UTC day in Redis: one hash per day
 * ({@code usage:api:yyyyMMdd}, field = workspace id), kept for 35 days. Only the count is stored —
 * never the path, payload, user or IP. Metering must never break a request, so Redis errors are
 * swallowed (the metric is then reported as unavailable).
 */
@Component
public class ApiUsageMeter {

    static final int RETENTION_DAYS = 35;
    private static final Logger log = LoggerFactory.getLogger(ApiUsageMeter.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redis;
    private final Clock clock;
    /** Day keys that already got their TTL from this instance (one EXPIRE per key per day). */
    private final Set<String> expiring = ConcurrentHashMap.newKeySet();
    private volatile long lastWarning;

    public ApiUsageMeter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void record(UUID workspaceId) {
        String key = key(LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        try {
            redis.opsForHash().increment(key, workspaceId.toString(), 1);
            if (expiring.add(key)) {
                redis.expire(key, Duration.ofDays(RETENTION_DAYS));
                if (expiring.size() > 64) {
                    expiring.clear(); // old days; the current key is re-added on its next request
                    expiring.add(key);
                }
            }
        } catch (RuntimeException e) {
            long now = System.currentTimeMillis();
            if (now - lastWarning > 60_000) {
                lastWarning = now;
                log.warn("API usage metering unavailable: {}", e.getMessage());
            }
        }
    }

    /**
     * Requests per workspace over the last {@code days} UTC days (today included), or {@code null}
     * when Redis cannot be read (unavailable, not zero).
     */
    public Map<UUID, Long> lastDays(int days) {
        Map<UUID, Long> totals = new HashMap<>();
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        try {
            for (int i = 0; i < days; i++) {
                Map<Object, Object> day = redis.opsForHash().entries(key(today.minusDays(i)));
                day.forEach((field, value) -> {
                    try {
                        totals.merge(UUID.fromString(field.toString()), Long.parseLong(value.toString()), Long::sum);
                    } catch (RuntimeException ignored) {
                        // a malformed field is skipped, never fatal
                    }
                });
            }
            return totals;
        } catch (RuntimeException e) {
            log.warn("API usage counters could not be read: {}", e.getMessage());
            return null;
        }
    }

    private static String key(LocalDate day) {
        return "usage:api:" + DAY.format(day);
    }
}

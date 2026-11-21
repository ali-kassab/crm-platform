package com.crmplatform.platform.application;

import com.crmplatform.files.Files;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Live infrastructure checks for the platform admin. Every status comes from a real call made now
 * (a query, a PING, a bucket metadata request) — never from configuration or a front-end guess.
 * The report carries no host names, connection strings, credentials or stack traces.
 */
@Service
public class PlatformHealth {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final Files files;
    private final MeterRegistry meters;
    private final Clock clock;

    public PlatformHealth(JdbcTemplate jdbc, StringRedisTemplate redis, Files files, MeterRegistry meters, Clock clock) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.files = files;
        this.meters = meters;
        this.clock = clock;
    }

    public Report check() {
        List<Component> components = new ArrayList<>();
        components.add(new Component("application", Status.UP, 0L, null));
        components.add(probe("database", () -> {
            jdbc.queryForObject("select 1", Integer.class);
            return true;
        }));
        components.add(probe("redis", () -> "PONG".equalsIgnoreCase(
                redis.execute((RedisCallback<String>) connection -> connection.ping()))));
        components.add(probe("objectStorage", files::storageReachable));
        components.add(new Component("mail", Status.NOT_CHECKED, null, "Not probed: mail is sent on demand"));

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Instant now = Instant.now(clock);
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Runtime runtime = new Runtime(now.minusMillis(uptimeMs), uptimeMs / 1000, heap.getUsed(),
                heap.getMax() < 0 ? null : heap.getMax(), ManagementFactory.getThreadMXBean().getThreadCount(),
                java.lang.Runtime.getRuntime().availableProcessors());

        Requests requests = requests();
        Database database = database();
        Status overall = components.stream().anyMatch(c -> c.status() == Status.DOWN) ? Status.DEGRADED : Status.UP;
        return new Report(now, overall, components, runtime, requests, database);
    }

    private Component probe(String name, Supplier<Boolean> call) {
        long start = System.nanoTime();
        try {
            boolean ok = Boolean.TRUE.equals(call.get());
            long ms = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new Component(name, ok ? Status.UP : Status.DOWN, ms, ok ? null : "Did not answer as expected");
        } catch (RuntimeException e) {
            long ms = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new Component(name, Status.DOWN, ms, "Unreachable"); // the exception text may hold a host name
        }
    }

    /** HTTP requests since this instance started, by outcome (from the Micrometer server timer). */
    private Requests requests() {
        long total = 0;
        long clientErrors = 0;
        long serverErrors = 0;
        for (Timer timer : meters.find("http.server.requests").timers()) {
            long count = timer.count();
            total += count;
            String outcome = timer.getId().getTag("outcome");
            if ("CLIENT_ERROR".equals(outcome)) {
                clientErrors += count;
            } else if ("SERVER_ERROR".equals(outcome)) {
                serverErrors += count;
            }
        }
        return new Requests(total, clientErrors, serverErrors);
    }

    private Database database() {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                    select (select version from flyway_schema_history where success
                            order by installed_rank desc limit 1) as schema_version,
                           (select count(*) from event_publication where completion_date is null) as pending_events,
                           (select count(*) from pg_stat_activity where datname = current_database()) as connections
                    """);
            return new Database((String) row.get("schema_version"), ((Number) row.get("pending_events")).longValue(),
                    ((Number) row.get("connections")).longValue());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public enum Status { UP, DOWN, DEGRADED, NOT_CHECKED }

    public record Component(String name, Status status, Long latencyMs, String note) {
    }

    public record Runtime(Instant startedAt, long uptimeSeconds, long heapUsedBytes, Long heapMaxBytes, int threads,
            int processors) {
    }

    /** Counts since this instance started (they reset on restart). */
    public record Requests(long total, long clientErrors, long serverErrors) {
    }

    public record Database(String schemaVersion, long pendingEvents, long connections) {
    }

    public record Report(Instant checkedAt, Status overall, List<Component> components, Runtime runtime,
            Requests requests, Database database) {
    }
}

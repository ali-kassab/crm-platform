package com.crmplatform.realtime;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Realtime relay to the NestJS service (ADR-0015). */
@ConfigurationProperties("app.realtime")
public record RealtimeProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("rt:events") String channel,
        @DefaultValue("60s") Duration ticketTtl) {
}

package com.crmplatform.audit.application;

import com.crmplatform.audit.domain.AuditEntry;
import com.crmplatform.audit.domain.AuditEntryRepository;
import com.crmplatform.shared.events.DomainEvents.AuditRecorded;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Persists every {@link AuditRecorded} event synchronously, inside the transaction that made the
 * change: the change and its audit row commit (or roll back) together (BR-111).
 */
@Component
class AuditRecorder {

    private final AuditEntryRepository entries;
    private final ObjectProvider<Tracer> tracer;
    private final Clock clock;

    AuditRecorder(AuditEntryRepository entries, ObjectProvider<Tracer> tracer, Clock clock) {
        this.entries = entries;
        this.tracer = tracer;
        this.clock = clock;
    }

    @EventListener
    void on(AuditRecorded event) {
        AuditEntry.Header header = new AuditEntry.Header(event.workspaceId(), event.actorUserId(), event.actorLabel(),
                traceId(), Instant.now(clock));
        entries.save(new AuditEntry(header, event.action(), event.entityType(), event.entityId(), event.projectId(),
                event.jobId(), event.changes()));
    }

    private String traceId() {
        Tracer t = tracer.getIfAvailable();
        Span span = t == null ? null : t.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}

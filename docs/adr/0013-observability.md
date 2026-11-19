# ADR-0013: Observability

- Status: Accepted (2026-11-18)
- Actuator health groups (liveness, readiness), Micrometer → Prometheus, OpenTelemetry tracing (OTLP exporter, off by default locally), and Spring Boot structured JSON logging with `traceId`, `workspaceId` and `userId`.
- Prometheus and Grafana run under the Compose `observability` profile.

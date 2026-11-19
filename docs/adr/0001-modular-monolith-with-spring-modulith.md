# ADR-0001: Modular monolith with Spring Modulith

- Status: Accepted (2026-11-18)
- **Context:** there are 10 cohesive business areas, a single team, and a CV/portfolio goal. Microservices would add operational cost with no benefit.
- **Decision:** one Spring Boot application.
  - The top-level package per module is `com.crmplatform.<module>`.
  - Spring Modulith verifies the module boundaries in a test.
  - Modules interact through `api` packages (public services/DTOs) and domain events. Event publications are persisted in the Modulith event registry (JDBC).
  - Inside each module:
    - the rich modules (automation, work/board, finance) use `domain` / `application` / `infrastructure` / `web` packages;
    - the CRUD-heavy modules use `web` → `application` → `persistence` with thin entities.
- **Consequences:** a simple deployment (one container) with enforceable boundaries. A module can be extracted later if ever needed.


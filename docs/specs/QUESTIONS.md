# Questions & Decisions Log

All questions raised so far are **answered**. Decisions are recorded here so the implementation doesn't depend on any other material.

| ID | Topic | Decision |
|---|---|---|
| D1 | Packaging | Standalone application |
| D2 | Identity | The app owns authentication (sign-up, login, refresh, reset, invites, roles) |
| D3 | Technologies | Java/Spring Boot, Spring Security, JPA/PostgreSQL, Flyway, Redis, JUnit/Mockito/Testcontainers, Docker/Compose, GitHub Actions, AWS (design), Terraform, Actuator/Micrometer/OTel/Prometheus/Grafana, OpenAPI |
| D4 | Repo | Monorepo: backend, frontend, infra, docs |
| D5 | Frontend | React + Tailwind + shadcn/ui; no hand-built UI primitives |
| Q1a | Sign-up | Self sign-up creates a workspace; teammates by invite |
| Q1b | Extra auth | None (no email verification, MFA, OAuth) |
| Q2 | Scope | All business areas |
| Q3 | API style | Clean REST + Problem Details |
| Q4 | Real-time | STOMP over WebSocket |
| Q4a | Frontend hosting (design) | S3 + CloudFront |
| Q5 | Database | PostgreSQL |
| Q6 | Messaging | Email and SMS stubbed (Mailpit locally) |
| Q7 | Data | Start empty + demo seed |
| Q9 | AWS | Terraform written and validated, never applied; local runtime |
| Q10 | Custom | None |
| Q-11 | Unknown platform behaviors | Specified fresh: soft delete hidden everywhere, auth everywhere, Problem Details |
| Q-12 | User HR fields | Keep jobTitle and phone only |
| Q-13 | Note parent | Exactly one of contact, project, job |
| Q-14 | Flowcharts | Store and edit the diagram in the app (NF-01) |
| Q-15 | Status triggers | Implement (NF-02) |
| Q-16 | Delay semantics | Relative to the previous step |
| Q-17 | Geocoding | Port with a stub adapter; coordinates optional and manual |
| Q-18 | Platform extras | Keep in-app notifications; drop chat, call/SMS activity, usage metering |
| Q-19 | Recurring service scheduling | Out of scope |
| Q-20 | HTML upload | Dropped |
| Q-21 | Localization | E.164 phones, no default country, ISO dates in the API, locale formatting in the UI |
| Q-22 | Extra automation commands | Implement CREATE_TRANSACTION; drop CREATE_CHARGE and REQUEST_REVIEW |
| Q-23 | Overpayment | Allowed; shown as overpaid |
| Q-24 | Transaction corrections | Void with reason (NF-03) |
| Q-25 | Team access level | Removed |
| Q-26 | Roles | Owner, Admin, Member, Viewer + platform super-admin flag |

Open: **LICENSE**. MIT is proposed for the portfolio; it is added as a placeholder and confirmed before the repo is ever made public.

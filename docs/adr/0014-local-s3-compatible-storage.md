# ADR-0014: Local S3-compatible storage

- Status: Accepted (2026-11-18)

## Context
ADR-0009 needs an S3-compatible object store for local development and tests. The Docker Hub images of the most common choice are no longer published, so we need a maintained alternative.

## Decision
- **Development stack (Docker Compose):** `rustfs/rustfs:1.0.6`. It is S3-compatible, Apache-2.0 licensed, actively released, persists to a volume and has a web console.
- **Integration tests (Testcontainers):** `adobe/s3mock:5.2.3`. It is lightweight, starts fast and needs no credentials.
- The application talks to both through the AWS SDK v2 with an endpoint override and path-style access. Production (design only) uses Amazon S3 with the same code.

## Consequences
- No vendor-specific client code. Switching stores is configuration only.
- RustFS behaviour may differ from S3 in edge cases. Presigned-URL and content-type behaviour are covered by tests against S3Mock and documented in the runbook.

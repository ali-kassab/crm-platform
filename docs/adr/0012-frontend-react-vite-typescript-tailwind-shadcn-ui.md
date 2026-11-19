# ADR-0012: Frontend: React + Vite + TypeScript + Tailwind + shadcn/ui

- Status: Accepted (2026-11-18)
- UI is built only from shadcn/ui components, blocks and vetted shadcn-registry components (kanban board, flowchart editor via React Flow).
- TanStack Query for server state, React Router, react-hook-form + zod, and an OpenAPI-generated typed client.
- In production the SPA is served from the same origin as the API (nginx locally, CloudFront in the AWS design).


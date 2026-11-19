# Frontend

Arabic-first (RTL, English second) React app for the field-service platform.

**Stack:** React 19 · Vite 8 · TypeScript 5.9 · Tailwind CSS 4 · shadcn/ui (radix, RTL enabled) ·
TanStack Query · React Router · i18next · react-hook-form + zod · Playwright.
UI components come from shadcn/ui only (`pnpm dlx shadcn@latest add <name>`); don't hand-build them.

Needs Node 24 (portable copy in `D:\Workspace\tools\node-v24.22.3-win-x64`) and pnpm.

```bash
pnpm install
pnpm dev            # http://localhost:5173, proxies /api to http://localhost:8080 (VITE_API_PROXY to change)
pnpm build          # type-check, build, then enforce the bundle budget (scripts/check-bundle.mjs)
pnpm test:e2e       # Playwright against the built app with a mocked API (ar/en, RTL/LTR screenshots)
```

- Language: Arabic by default, remembered per device (`localStorage`), `<html lang/dir>` follows it;
  every request sends `Accept-Language`, so server errors arrive already translated.
- Auth: the access token lives in memory only; the refresh token is an HttpOnly cookie restored via
  `POST /api/v1/auth/refresh` on load.
- Screenshot baselines are per OS (`*-win32.png` today); generate Linux ones in CI with
  `pnpm test:e2e:update`.

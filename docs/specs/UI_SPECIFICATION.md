# UI Specification (React + Tailwind + shadcn/ui)

**Hard rule:** every visual element comes from shadcn/ui components, shadcn blocks, or approved shadcn-compatible registry components. Feature code only composes them. Icons come from `lucide-react`. Theme: shadcn CSS variables, light and dark (toggle via `DropdownMenu`).

Approved registry components (ADR in repo): **Kanban** (Dice UI or Kibo UI kanban, built on dnd-kit) and **Flow editor** (React Flow `@xyflow/react` with its shadcn UI components). shadcn "Data Table" pattern = `Table` + TanStack Table.

## App shell
- **Layout:** shadcn **`sidebar-07`** block (collapsible icon sidebar), plus a top bar with `Breadcrumb`, a global search `Command` (⌘K), a notifications `Popover` with `Badge`, and a user `DropdownMenu` (profile, switch workspace, theme, logout).
- **Sidebar groups:**
  - Dashboard
  - CRM: Contacts, Locations
  - Work: Projects, Tasks, Flowcharts
  - Operations: Jobs, Warranties
  - Finance: Transactions
  - Automation: Procedures, Runs
  - Settings: Workspace, Members, Reference data, Board templates, Milestone templates, Audit log
- Items are hidden when the user lacks `*:read`.
- Feedback: `sonner` toasts. Confirmations: `AlertDialog`. Loading: `Skeleton`. Empty states: `Card` + `Button`.
- Forms: `Form` (react-hook-form + zod) with `Input`, `Textarea`, `Select`, `Combobox` (`Popover` + `Command`), `DatePicker` (`Popover` + `Calendar`), `Checkbox`, `Switch`, `RadioGroup`, and a `Multi-select` (Badge + Command pattern).

## Screens
| Route | Screen | Perm | Data | Built from |
|---|---|---|---|---|
| /login | Login | 🔓 | POST /auth/login | `login-03` block |
| /register | Sign-up | 🔓 | POST /auth/register | `signup-03`-style block (Card + Form) |
| /forgot-password, /reset-password | Password reset | 🔓 | F6 | Card, Form, Input, Button |
| /accept-invite | Accept invite | 🔓 | F8 | Card, Form, Alert |
| /select-workspace | Workspace picker | auth | GET /me | Card list, Avatar |
| / | Dashboard | reports:read (others see "My tasks") | /reports/dashboard | `dashboard-01` block: KPI Cards, `Chart` (bar/area/pie), Table, `Tabs` for period, `ToggleGroup` |
| /contacts | Contacts list | crm:read | GET /contacts | Data Table with toolbar (Input search, faceted filters via Popover + Command, column visibility DropdownMenu), row actions DropdownMenu, `Pagination`, bulk select + AlertDialog for batch delete |
| /contacts/new, /contacts/:id/edit | Contact form | crm:write | POST/PATCH | `Sheet` or page Form, `Tabs` (Details, Address, Metadata), image upload with `Input type=file` + `Avatar` preview |
| /contacts/:id | Contact detail | crm:read | GET contact, accounting, locations, notes, milestone | Card header with `Badge` (type, status, tags), `Tabs`: Overview / Locations / Projects / Notes / Milestone / Finance / Automation runs |
| /locations, /locations/:id | Locations | crm:read | | Data Table; detail Card with working-hours Table |
| /projects | Projects list | work:read | | Data Table + view toggle (`ToggleGroup`) to a Card grid with `Progress` for budget and milestone |
| /projects/new | Create project | work:write | | Multi-step form via `Tabs` (Basics → Location & Team → Board template → Milestone) |
| /projects/:id | Project workspace | work:read | | `Tabs`: Overview (Cards, Progress), **Board**, Jobs (Table), Team (Table + Dialog add member), Finance (KPI Cards + transactions Table), Notes, Flowcharts, Warranties, Activity (audit) |
| /projects/:id/board, /jobs/:id/board | Kanban board | work:read (drag needs work:write) | GET board + STOMP | **Registry Kanban** component; card = `Card` with `Badge` priority, `Avatar` group, due date. Card click → `Sheet` task detail (Form + comments list + `Textarea`). Column header menu `DropdownMenu` (rename, set default, delete → AlertDialog with moveTo `Select`) |
| /tasks | My / all tasks | work:read | GET /tasks | Data Table with status Tabs, overdue `Badge` |
| /jobs, /jobs/:id | Jobs | ops:read | | Data Table; detail Tabs: Overview, Board, Team, Files (grid of Cards with presigned links), Finance, Notes |
| /warranties | Warranties | ops:read | | Data Table + Dialog form; status Badge |
| /transactions | Transactions | finance:read | | Data Table with type/method filters and date range (Calendar range); create `Dialog` Form with conditional fields; void AlertDialog with reason |
| /flowcharts, /flowcharts/:id | Flowcharts | work:read | | Card grid (favorite toggle); editor page: **React Flow** canvas, shadcn `Toolbar` (Button group), node inspector `Sheet`; save with version |
| /automation/procedures, /:id | Procedure editor | automation:read/manage | GET /automation/commands | Table list; editor: left actions list (sortable, from the registry Kanban/Sortable pattern) + per-action Form generated from the command schema; optional flow preview (React Flow read-only) |
| /automation/runs | Runs | automation:read | | Data Table; detail `Sheet` with step `Timeline` built from Card + Badge per step |
| /settings/workspace | Workspace | workspace:manage | | Form; danger zone Card + AlertDialog (type the name to confirm) |
| /settings/members | Members & invites | members:manage (read for all) | | Data Table, role `Select`, invite `Dialog`, pending invites Table |
| /settings/reference-data | Categories, statuses, tags | refdata:manage | | `Tabs` with 3 Data Tables; color input (Input type=color) + icon Combobox |
| /settings/board-templates, /settings/milestone-templates | Templates | templates:manage | | Table + Dialog Form with column/step list |
| /settings/audit | Audit log | audit:read | | Data Table + `Sheet` with JSON diff (`ScrollArea` + `pre`) |
| /profile | My profile | auth | GET/PATCH /me, password change | Card + Form |

## Behavior requirements
- **Auth:** the access token is kept in memory. A single-flight refresh happens on 401 `TOKEN_EXPIRED`. Logout clears the query cache.
- **Permissions:** `usePermission('crm:write')` hides or disables actions, and a `Tooltip` explains why something is disabled.
- **URL state:** list filters, sort and page live in search params.
- **Optimistic UI:** kanban drag-and-drop updates optimistically, then rolls back and refetches on 409.
- **Real-time:** the board subscribes to `/topic/boards/{id}` and the notification bell to `/user/queue/notifications`.
- **Forms:** zod schemas mirror the server rules. Server `errors[]` map onto field errors.
- **Responsive:** sidebar becomes a `Sheet` on < 768 px. Tables scroll horizontally inside `ScrollArea`.
- **Accessibility:** Radix primitives; every icon button has an aria-label; focus is managed in Dialog and Sheet; axe checks run in tests.
- **i18n-ready:** all copy in `src/i18n/en.json`. Dates and money formatted with `Intl` using the workspace locale.

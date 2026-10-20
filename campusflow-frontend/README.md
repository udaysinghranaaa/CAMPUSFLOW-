# CampusFlow frontend

React + strict TypeScript + Vite frontend for the existing Spring Boot CampusFlow API.

## Local development

Use Node **24.15.0 or later in the 24.x LTS line** (verified here: 24.15.0), npm and Chrome for browser tests.

```powershell
Set-Location C:\CAMPUSFLOW\campusflow-frontend
npm.cmd ci --cache .artifacts/npm-cache
Copy-Item .env.example .env.local
npm.cmd run dev
```

Open **http://localhost:5173**. The public `.env.local` setting is only
`VITE_API_BASE_URL=http://localhost:8080`; restart Vite after changing it. Never put secrets in VITE variables.
Backend must be running separately; no fake/mock login is shipped. Default backend CORS permits exactly localhost:5173.

In another terminal, the optional helper starts the existing backend with private process-only credentials:

```powershell
Set-Location C:\CAMPUSFLOW\campusflow-frontend
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-LocalBackend.ps1
```

It prompts privately for DB_PASSWORD, generates a process-only JWT key if absent, and invokes Maven on the
existing backend. No backend files, database roles or authentication settings are changed. Existing users
must already be provisioned; enter your own credentials in the browser, never in chat or source files.
The generated key changes after a fresh helper run unless you supply your own protected process environment.

## Checks

```powershell
npm.cmd run typecheck
npm.cmd run lint
npm.cmd test
npm.cmd run build
npm.cmd run test:browser
npm.cmd run preview
```

Browser checks use installed Chrome and a temporary Vite server at 5173. Stop any existing server on that
port before running them. Browser tests **mock HTTP** and do not prove PostgreSQL/backend integration.
`preview` serves production output at http://localhost:4173; backend CORS defaults to 5173, so use dev for
integration unless an administrator explicitly configures 4173 through the existing backend environment.
Vite preview is not a production server. Future static hosting must rewrite non-asset routes to index.html.

## Delivered features

ADMIN: real-count overview and department/faculty/student/batch/section/account management.
FACULTY/STUDENT: own profile, reduced faculty directory, role-aware overview. Shared password change,
logout, light/dark mode, responsive navigation, native accessible dialogs, server-error feedback and pagination.
Student enrollment is readonly during edit. No student delete or public ADMIN provisioning is offered.

**Session policy:** both credentials live only in memory. In-tab access renewal is deduplicated; refresh
POSTs are never automatically retried. Reloading/closing the tab requires sign-in again. Neither token is
written to localStorage, sessionStorage or IndexedDB; only the theme preference is saved. Cookie persistence
requires a separately approved backend contract; see API-AUDIT.md.

## Verification status

See FRONTEND-PHASE4.5.md for actual results, blocked real integration, manual smoke tests, security proposal,
Hinglish explanations, interview preparation and file inventory. Do not call Phase 4.5 fully verified until
real backend login, role boundaries and CRUD checks pass locally.

## Git

This sibling folder has no Git repository or remote initialized. Recommend a separate repository named
**CAMPUSFLOW-FRONTEND**. The backend repository/history remain untouched. Ignore files exclude node_modules,
dist, test artifacts, local environments and logs. No automatic commits, pushes or deployment.

## Phase5

See [FRONTEND-PHASE5.md](FRONTEND-PHASE5.md) for Faculty Finder, academic scheduling, secure verification, actual results and remaining live-integration gates. Existing memory-only session policy is unchanged.

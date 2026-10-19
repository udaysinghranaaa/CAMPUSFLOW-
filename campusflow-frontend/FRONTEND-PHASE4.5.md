# CampusFlow — Phase 4.5 implementation and learning report

## Delivery and evidence

Created **C:\CAMPUSFLOW\campusflow-frontend**, a sibling of the existing backend.
No parent/frontend Git repository initialized, no remote created, no commit/push/deployment.
Backend clean baseline HEAD: `151ec999f0a5cb049a43dfd17cadde1b9cb4698d`.
The supplied brief reports backend Phase 4 tests and cleanup passing. Those earlier results are user-provided,
not rerun as frontend evidence. No backend files were edited for this phase.

Read **API-AUDIT.md** for the pre-implementation audit of actual controller/DTO/security contracts, exact
payloads, fields, filters, status codes, permission mapping and proposed cookie contract.

Initial compatible install rejected resolvers6 due to conflicting optional effect/typeschema peers.
Pinned compatible resolvers5.2.2; installation succeeded without force or legacy-peer-deps.
TypeScript6.0 is retained because typescript-eslint8 explicitly supports <6.1, not TypeScript7.
package-lock.json captures exact installed dependencies. Vite8, React19, Router7, Tailwind4, Query5,
RHF7, Zod4, Lucide1, Vitest5, RTL16, ESLint10 and Playwright1 are used.

Verification commands and final counts are recorded below after final checks. Real integration is BLOCKED:
GET http://localhost:8080/api/campus/health was unreachable, and private login credentials are unavailable.
No real login/CRUD success is claimed. The frontend can be implemented and checked independently;
**Phase 4.5 cannot be declared fully verified until real backend browser smoke checks pass.**

## Implemented routes and permissions

| Route                                          | Permission and behavior                                                                                       |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| /login                                         | Guest form, validation, password visibility, loading/error and role redirect                                  |
| /app                                           | Authenticated redirect to server-provided role home                                                           |
| /app/profile                                   | Own profile from auth/me only; no client-submitted owner ID                                                   |
| /app/change-password                           | Current/new/confirm validation; successful update ends session                                                |
| /admin                                         | ADMIN, six real API totals, functional management links                                                       |
| /admin/departments                             | ADMIN list/exact-code filter, create/edit/details/confirmed delete                                            |
| /admin/faculty                                 | ADMIN name/department/active filters, create/edit/details/status/confirmed delete                             |
| /admin/students                                | ADMIN name/enrollment/department/batch/section/semester/status filters, create/edit/details/status; no delete |
| /admin/batches                                 | ADMIN department filter, create/edit/details/confirmed delete                                                 |
| /admin/sections                                | ADMIN batch filter, create/edit/details/confirmed delete                                                      |
| /admin/accounts                                | ADMIN paginated accounts/details/provision linked Faculty or Student/status; own ADMIN status disabled        |
| /faculty, /student                             | Respective role-specific home and clearly upcoming modules                                                    |
| /faculty/profile, /student/profile             | Respective role, own profile                                                                                  |
| /faculty/directory, /student/faculty-directory | Respective role, reduced directory only                                                                       |
| /unauthorized, unmatched paths                 | Friendly recovery links; no forbidden records fetched                                                         |

AuthGuard/GuestGuard/RoleGuard improve navigation; Spring Security remains the authority on every request.
Faculty/Student never call private student lists or full faculty-management APIs. Department/batch/section
metadata is read only through the backend's shared authenticated policy. Directory supports pagination,
not invented search filters. Accounts have neither edit-email nor delete endpoints, so no such UI exists.
Batch/section active values are changed by their actual PUT request, not invented PATCH status routes.

All form writes serialize explicit request fields. Details show only whitelisted safe metadata.
Reference choices page through all results (20/page); selected off-page IDs remain visible.
Student batch choices filter by department, sections by batch, and parent changes clear dependent choices.
The backend checks the same relationships and all conflicts, including concurrent changes.
Enrollment remains readonly during edit. Optional section/capacity/text values serialize to null.
Destructive deletion and status changes require confirmation; 409 retains the dialog and explains the conflict.
Pagination totals come directly from PageResponse, never fabricated growth, activity or percentage data.

## Authentication and security design

Login sends {email,password}; successful TokenResponse is parsed, credentials remain in private class fields,
and auth/me supplies the actual role/profile. Client-visible JWT contents are never trusted to select ownership.
No passwords/tokens are logged, hardcoded, included in URLs, cached in Query data or written to browser storage.
The persisted theme key is `campusflow-theme`; all other application state is memory-only.
Sensitive mutations have gcTime0; forms unmount after success. No persisted Query cache is configured.
Both access AND refresh credentials are memory-only, not just access.

Before a request near expiry, one shared refresh promise rotates credentials. Concurrent 401s also share
refresh and retry their rejected request once. A late 401 sent with the previous token reuses the newly
rotated token rather than replaying the old refresh. Refresh POST has no retries; network uncertainty
clears the session because rotation may already have committed. 403 preserves identity and reports denial.
Every request has a 15-second timeout and supports abort signals; session generations reject late responses
after logout/account changes. Logout clears local identity/cache even if remote revocation fails. Password
change's invalid-current-password401 is displayed without pointless refresh/replay; success clears sessions.
API redirects are rejected so credential-bearing fetches are not followed to unexpected destinations.
Remote API origins require HTTPS; only loopback HTTP is accepted for development.

Reload limitation: no reload-persistent credential exists under this contract. Restore starts in a loading
state, then honestly resolves anonymous. The login page explains that reload/close requires sign-in.
Do not add localStorage/sessionStorage/IndexedDB persistence to hide this limitation. Closing a tab cannot
reliably revoke a server refresh row; explicit logout revokes it, otherwise backend expiry applies.
Memory reduces persistent theft exposure, but it does not make XSS harmless. Use reviewed dependencies,
HTTPS and a deliberate production CSP when hosting is approved; never render untrusted HTML.

The backend returns refresh in JSON, not HttpOnly cookies. **No backend security changes were made.**
API-AUDIT.md includes the narrow future proposal: HttpOnly + Secure production cookie, appropriate SameSite,
explicit cookie lifecycle/path/expiry, credentialed exact-origin CORS, CSRF/Origin checks and refresh/logout
contract changes. It needs explicit approval and backend security tests before persistent browser sessions.

## Components, structure and data flow

AppShell owns the role-aware sidebar, topbar, breadcrumbs, profile menu, logout and theme toggle.
Native dialog provides browser focus trapping and Escape behavior; mutations lock closing while in flight.
Mobile navigation traps keyboard focus, closes on Escape, restores trigger focus and makes main content inert.
Route changes focus main content. Skip link, labeled controls, aria-invalid/describedby, live notices,
reduced-motion support and horizontally scrollable table regions support keyboard/small-screen use.
Tables scroll within their own region; page/body do not need horizontal scrolling.

Data flow: React form -> Zod/RHF -> typed resources service -> ApiClient bearer fetch -> actual Spring
controller -> secured service -> repository/Hibernate -> PostgreSQL -> DTO -> Query cache -> UI.
Example: EntityForm creates a DepartmentRequest; success invalidates ['departments'] list/summary/choices,
shows a success notification and closes the form. Failure preserves inputs and maps server fieldErrors.
No SQL, DB credentials or signing keys belong in frontend code.

Feature-based code has a shared management engine because the six existing APIs share paginated CRUD
patterns. Each domain's actual fields, schema, filters and typed payload live in definitions.ts. Authentication,
profile, directory and dashboard stay separate. There are no fake future business modules or backend changes.

## Easy Hinglish learning guide

1. **React + Spring communication:** React browser mein UI render karta hai; fetch JSON request Spring ko bhejta hai.
   `resources.create('departments', body)` actual POST endpoint call karta hai. Database ko browser direct access nahi karta.
2. **Sibling folders:** frontend ka npm/Vite lifecycle aur backend ka Maven lifecycle independent hai. Isliye backend history,
   pom aur migrations accidentally replace nahi hote. Yeh MERN ke client/server separation jaisa hai.
3. **React vs Next.js:** React UI library hai; yahan Vite client SPA banata hai. Next.js routing/server rendering ka framework hai.
   Existing Spring already backend hai, isliye unnecessary second backend/SSR add nahi kiya.
4. **Components/props:** `Pagination` ko page/totalPages/onPage props milte hain. Same component six management pages mein
   reuse hota hai; parent state aur API queries control karta hai. Express/EJS se alag browser component composition hai.
5. **TypeScript types:** `StudentRequest` compiler ko exact body shape batata hai; `Student` response fields alag hain.
   Types runtime validation nahi hain. Zod user input validate karta hai, Spring final authority hai.
6. **Router/protected routes:** AuthGuard login check karta hai, RoleGuard role check. Student URL change karke admin UI
   nahi khol sakta; agar API directly call kare toh Spring ko phir bhi 403 dena chahiye.
7. **Query vs RTK Query:** TanStack Query fetching/cache/invalidation manage karta hai bina Redux store ke. RTK Query Redux
   Toolkit ecosystem ke saath suit karta hai. Is application mein unnecessary Redux dependency nahi chahiye.
8. **Fetch/Axios:** dono HTTP clients hain. Yahan typed fetch wrapper headers, timeout, cancellation, safe errors aur refresh
   centralize karta hai; har component mein repeated fetch/token code nahi hai.
9. **JWT login:** email/password -> BCrypt-backed server login -> token pair -> auth/me -> role home. Login form role select
   nahi karta. Server account role trusted source hai.
10. **Access vs refresh:** access short-lived API credential; refresh longer-lived random credential jo rotate hota hai.
    Same refresh ko do baar use karna replay defense trigger kar sakta hai, isliye refresh deduplication essential hai.
11. **HttpOnly cookies:** browser JS cookie value nahi read kar sakta. JSON refresh magically HttpOnly nahi ban sakta;
    server Set-Cookie contract zaroori hai. Cookie ke saath CSRF design bhi chahiye.
12. **RHF + Zod:** RHF form values/errors/submission coordinate karta hai. Zod schema rules declare karta hai. Password
    validation UTF-8 byte length check karti hai, sirf JavaScript string length nahi.
13. **CORS/CSRF:** CORS browser-origin policy hai, permission check nahi. CSRF automatic ambient credentials misuse karta hai.
    Current explicit bearer design aur future cookie design ko same security assumptions se handle nahi karna.
14. **Client/server validation:** client fast feedback deta hai; server DTO/service/SQL constraints data integrity protect karte hain.
    Mongoose validators ki tarah frontend Zod ko bypass kiya ja sakta hai; PostgreSQL constraints ko preserve karna zaroori hai.
15. **Caching/invalidation:** Query key ['departments','list',filters,page] results separate rakhta hai. Save ke baad resource
    prefix invalidate karne se list, choices aur real-count cards refetch hote hain. Logout private cache clear karta hai.
16. **Error boundary/loading:** render error par ErrorBoundary safe recovery dikhata hai; request error query state handle karti hai.
    Loading skeleton data aane tak honest pending state hai, fake zero count nahi.
17. **Responsive UI:** grids width ke saath adapt hote hain, mobile sidebar overlay banta hai, dialogs viewport ke andar rehte hain.
    Wide tables ka horizontal scroll table region tak limited hai; body overflow hide karke bug mask nahi kiya.
18. **MERN comparison:** React side familiar hai; Express middleware ki jagah Spring filter + @PreAuthorize API authorize karte hain.
    Mongoose refs ki jagah PostgreSQL foreign keys department/batch/section consistency enforce karte hain.
19. **Hooks/state:** local state filters/dialog/theme ke liye; remote state Query mein; identity AuthProvider external-store subscription
    mein. Har piece ko global Redux store mein rakhne ki zaroorat nahi.
20. **Permissions and ownership:** own profile `/auth/me` se aata hai, user-provided studentId se nahi. React mein hidden button security
    boundary nahi; server principal and relationship ownership final check hain.

## Run and manual real-backend smoke verification

Terminal A (private process-only database password prompt; no saved User Environment Variable):

```powershell
Set-Location C:\CAMPUSFLOW\campusflow-frontend
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-LocalBackend.ps1
```

Terminal B:

```powershell
Set-Location C:\CAMPUSFLOW\campusflow-frontend
npm.cmd ci --cache .artifacts/npm-cache
Copy-Item .env.example .env.local
npm.cmd run dev
```

Open http://localhost:5173 (not 127.0.0.1; the backend default CORS origin is exact). Enter your existing
credentials privately in the browser. Never paste them into the docs or a shared transcript.

Real integration checklist (pending; do not confuse with mocked tests):

1. Check GET /api/campus/health returns UP. Login with existing ADMIN; me role must drive /admin.
2. Compare each summary total with the real API's totalElements. Load all six management pages.
3. Use a unique verification marker and ONLY new records: department, faculty, batch, section, student;
   validate required inputs, edit fields, status transitions, and linked dropdown pagination/dependencies.
4. Try deleting a referenced department/section/batch; expect409 and preserved records. Student enrollment stays readonly.
5. Provision Faculty/Student accounts ONLY for these newly created profiles, never ADMIN. Login in separate browser contexts;
   verify own-profile metadata/directory, reject direct /admin and the other role portal. Do not test with other users' IDs.
6. Wait for access expiry (default900s), then use a list/profile; verify one refresh during simultaneous requests and continued UI.
   Do not copy token-bearing Network responses into logs/screenshots. Storage should contain only theme, never tokens/passwords.
7. Change password only on a verification-owned account, verify re-login requirement; logout each test session. Reload requires login.
8. Test400/401/403/404/409 and backend unavailable/network timeout safely. 403 must not turn into refresh loops.
9. Check375/768/1280px light/dark, keyboard Tab/Shift+Tab, sidebar Escape, dialog focus/close and table scroll without body overflow.
10. Clean only captured verification fixtures, accounts/tokens first. There is deliberately no public student/account DELETE API.
    Reuse the backend's proven verification-owned cleanup flow; do not manually broaden deletion or delete real academic data.
    New manual fixtures require a captured ownership manifest and a reviewed targeted cleanup, so use disposable approved fixtures.
11. Run existing backend Verify-Phase4.ps1 in your own terminal for its independent real API/security/fixture-cleanup checks.
    This proves the backend independently; UI integration must still be exercised through this frontend checklist.

## Interview preparation — 24 questions and answers

1. **What does React do?** It composes UI from components and reconciles updates from state/props.
2. **What is JSX?** JavaScript-compatible syntax transformed into React elements, not an HTML template sent straight to the server.
3. **Props vs state?** Props come from a parent; state belongs to a component or an explicit external store.
4. **Why TypeScript strict?** It catches missing/null/wrong-shaped data uses early; it does not validate HTTP at runtime.
5. **Why different request/response types?** Responses contain IDs/audits/status fields that must not silently become writable body fields.
6. **Why stable list keys?** Keys preserve the correct component identity when rows change; indexes can associate state with the wrong row.
7. **Controlled vs uncontrolled forms?** Controlled inputs mirror React state. RHF can register native inputs efficiently and uses Controller for custom controls.
8. **What does Zod add?** Runtime parsing and structured field errors; server validation must still run.
9. **useEffect purpose?** Synchronizing external systems/lifecycle, such as theme DOM changes and security store subscriptions, not duplicating derived state.
10. **How does a protected route work?** It gates rendering/navigation by identity/role; actual API permissions remain server-side.
11. **Why no client-side role selection?** The server chooses permissions from the authenticated account; client roles are attacker-controlled.
12. **What is TanStack Query?** A server-state cache with lifecycle, invalidation, errors and cancellation; not a replacement for all local UI state.
13. **Why include filters/page in query keys?** Different queries must not accidentally reuse each other's results.
14. **What happens after a successful mutation?** Invalidate affected resources so lists, choices and totals reflect persisted state.
15. **Why clear cache on logout?** It prevents a subsequent user from seeing the previous user's private cached records.
16. **Why memory-only tokens?** It avoids persistence across reload and limits durable token theft, with a deliberate re-login tradeoff; XSS can still affect an active page.
17. **Why not sessionStorage for refresh?** It remains JavaScript-readable persisted credentials, which violates this phase's safe temporary design.
18. **Why deduplicate refresh?** Rotating tokens cannot be reused concurrently without causing replay defense or revocation.
19. **Why not retry refresh on timeout?** The server may have consumed it before the client lost the response; replaying it is unsafe.
20. **401 vs403 in UI?** 401 may permit one refresh then sign-in;403 means permission denied and should not start a refresh loop.
21. **What is AbortController?** It cancels fetch work on navigation/timeouts; session generation checks additionally reject stale responses.
22. **Why HttpOnly does not solve CSRF?** JS cannot read that cookie, but the browser can still send it automatically in forged requests.
23. **Mocked tests vs integration?** Mocked boundaries prove frontend behavior against a chosen contract; only the live server/database can prove actual interoperability.
24. **Why Vite SPA rather than Next.js here?** Existing Spring APIs supply server functionality; SSR and a second server framework add scope without a current requirement.

## References

- [Vite guide](https://vite.dev/guide/) — development/build setup and Node compatibility.
- [React Router declarative installation](https://reactrouter.com/start/declarative/installation) — SPA routing.
- [TanStack Query overview](https://tanstack.com/query/latest/docs/framework/react/overview) — server-state lifecycle.
- Backend source and API-AUDIT.md are the authority for CampusFlow contracts.

## Final executed checks

Final checks executed on 2026-10-19, Node24.15.0/npm11.12.1:

| Executed command/check                                            | Actual result                                                        |
| ----------------------------------------------------------------- | -------------------------------------------------------------------- |
| npm.cmd install --cache .artifacts/npm-cache --no-fund --no-audit | Succeeded after selecting compatible resolver5.2.2; lockfile created |
| npm.cmd run typecheck                                             | PASS, strict TypeScript, exit0                                       |
| npm.cmd run lint                                                  | PASS, zero errors/warnings, exit0                                    |
| npm.cmd test                                                      | PASS, 4 files, 48 tests, zero failures, exit0                        |
| npm.cmd run build                                                 | PASS, exit0; Vite production bundle created, lazy domain chunks      |
| npm.cmd run test:browser -- --grep 'mobile keyboard focus'        | PASS after explicit focus-restoration fix                            |
| npm.cmd run test:browser                                          | PASS, all10 Chrome scenarios, 53.6s, exit0                           |
| npm.cmd audit --omit=dev --json --cache .artifacts/npm-cache      | PASS, 0 known production dependency vulnerabilities                  |
| PowerShell Parser on scripts/Start-LocalBackend.ps1               | PASS; startup itself not executed without credentials                |
| Production bundle scan                                            | No server-secret variable names or test login fixtures found         |
| Backend git status --porcelain / rev-parse HEAD                   | Clean, unchanged151ec999f0a5cb049a43dfd17cadde1b9cb4698d             |
| Frontend/parent .git existence                                    | Neither exists; no repository or remote initialized                  |

Chrome coverage includes375/768/1280px, all three roles, login/logout, six admin management pages,
department creation/refetch, immutable enrollment, own profile/directory, denied role navigation,
reload requiring sign-in, light/dark mode, theme-only storage, body overflow, mobile keyboard trap/Escape
and dialog focus return. Screenshots were visually reviewed for desktop/mobile/light/dark using fixture data.
These tests use intercepted HTTP; they are NOT proof of real Spring/PostgreSQL interoperability.
Playwright started and stopped the owned Vite development server; no server is intentionally left running.

Observed issues fixed before final results: ambiguous test selectors; mobile toast covering sidebar Sign out;
React dialog unmount losing trigger focus. The first sandboxed browser run also stalled during process teardown;
only its verified owned Vite process was stopped, and subsequent complete runs exited normally with local process permission.
Remaining test-harness warning: NO_COLOR and FORCE_COLOR both set by the host. It is not an application failure.
An earlier build showed a nonblocking bundler timing warning; the final build passed in880ms.

Remaining blocker: real backend health, login, refresh, CRUD and role smoke verification must be run locally.
Health at localhost:8080 was unavailable and no private DB/login credentials were accessed. No mock result
has been represented as actual backend integration. Full Phase4.5 completion remains pending that verification.
Cookie-based reload-persistent sessions remain a separately approved future security-contract change.

## Exact frontend file inventory

All following files are new in the sibling frontend. No backend source, config, migrations or Git metadata were changed.
Generated node_modules/dist, logs, npm cache and test screenshots/results are ignored artifacts, not delivery source.

| File                                             | Purpose                                                                         |
| ------------------------------------------------ | ------------------------------------------------------------------------------- |
| `.env.example`                                   | Public API URL example; contains no secrets                                     |
| `.gitignore`                                     | Excludes dependencies, build output, secrets/local env and artifacts            |
| `.prettierignore`                                | Consistent source formatting                                                    |
| `.prettierrc.json`                               | Consistent source formatting                                                    |
| `API-AUDIT.md`                                   | Pre-implementation source audit and exact backend contracts                     |
| `e2e/workspace.spec.ts`                          | Chrome responsive role/management scenarios against mocked HTTP                 |
| `eslint.config.js`                               | Strict lint and hook checks                                                     |
| `FRONTEND-PHASE4.5.md`                           | Hinglish learning, architecture, security, interview questions and verification |
| `index.html`                                     | Accessible document metadata and SPA entry                                      |
| `package.json`                                   | Compatible dependencies and npm commands                                        |
| `package-lock.json`                              | Reproducible exact dependency resolution                                        |
| `playwright.config.ts`                           | Installed Chrome, isolated test artifacts and owned Vite server                 |
| `public/favicon.svg`                             | Local SVG campus mark                                                           |
| `README.md`                                      | Setup, commands, session limits and Git recommendation                          |
| `scripts/Start-LocalBackend.ps1`                 | Private process-only credential prompt and existing backend startup             |
| `src/app/App.tsx`                                | Router composition, lazy pages and render-error recovery                        |
| `src/components/layout/AppShell.tsx`             | Sidebar/topbar/profile menu, keyboard navigation, logout and theme              |
| `src/components/ui/DataTable.tsx`                | Typed reusable scrollable table                                                 |
| `src/components/ui/feedback.tsx`                 | Accessible dialogs, states, pagination, badges and notices                      |
| `src/components/ui/toastContext.ts`              | Accessible dialogs, states, pagination, badges and notices                      |
| `src/features/auth/auth.test.tsx`                | Unit/component test or isolated test provider/fixture/setup                     |
| `src/features/auth/authContext.ts`               | Authentication, forms, role guards or private session subscription              |
| `src/features/auth/context.tsx`                  | Authentication, forms, role guards or private session subscription              |
| `src/features/auth/guards.tsx`                   | Authentication, forms, role guards or private session subscription              |
| `src/features/auth/LoginPage.tsx`                | Authentication, forms, role guards or private session subscription              |
| `src/features/auth/PasswordPage.tsx`             | Authentication, forms, role guards or private session subscription              |
| `src/features/auth/schemas.ts`                   | Authentication, forms, role guards or private session subscription              |
| `src/features/dashboard/DashboardPage.tsx`       | Real count admin overview or own role portal and honest future modules          |
| `src/features/dashboard/PortalPage.tsx`          | Real count admin overview or own role portal and honest future modules          |
| `src/features/directory/DirectoryPage.tsx`       | Paginated reduced faculty directory                                             |
| `src/features/management/definitions.test.ts`    | Unit/component test or isolated test provider/fixture/setup                     |
| `src/features/management/definitions.ts`         | Domain-specific fields, schemas, filters and explicit request serialization     |
| `src/features/management/EntityForm.tsx`         | Validated create/edit dialog with mapped server field errors                    |
| `src/features/management/management.test.tsx`    | Unit/component test or isolated test provider/fixture/setup                     |
| `src/features/management/ManagerPage.tsx`        | Admin lists, filters, details, status and confirmed deletion                    |
| `src/features/management/RelationshipPicker.tsx` | Paginated reference selection and parent filters                                |
| `src/features/profile/ProfilePage.tsx`           | Principal-owned profile and permitted academic metadata                         |
| `src/features/profile/useOwnProfile.ts`          | Principal-owned profile and permitted academic metadata                         |
| `src/main.tsx`                                   | React root, Query/Auth/Toast providers                                          |
| `src/services/apiClient.test.ts`                 | Unit/component test or isolated test provider/fixture/setup                     |
| `src/services/apiClient.ts`                      | Memory-only credentials, refresh concurrency, safe HTTP and session lifecycle   |
| `src/services/resources.ts`                      | Typed resource endpoints, pagination and query encoding                         |
| `src/styles/index.css`                           | Tailwind foundation, coherent theme, responsive UI and accessibility styles     |
| `src/test/fixtures.ts`                           | Unit/component test or isolated test provider/fixture/setup                     |
| `src/test/render.tsx`                            | Unit/component test or isolated test provider/fixture/setup                     |
| `src/test/setup.ts`                              | Unit/component test or isolated test provider/fixture/setup                     |
| `src/types/api.ts`                               | Actual response/request and role/status types                                   |
| `tsconfig.json`                                  | Strict TypeScript/compiler environment                                          |
| `vite.config.ts`                                 | Vite React/Tailwind integration and Vitest configuration                        |

## Final folder architecture

```text
C:/CAMPUSFLOW/
  campusflow-backend/           existing independent Git repository, untouched
  campusflow-frontend/          new independent frontend folder, no Git initialized
    API-AUDIT.md
    FRONTEND-PHASE4.5.md
    README.md
    public/favicon.svg
    scripts/Start-LocalBackend.ps1
    e2e/workspace.spec.ts
    src/
      main.tsx
      app/App.tsx
      components/
        layout/AppShell.tsx
        ui/{DataTable,feedback}.tsx
        ui/toastContext.ts
      features/
        auth/                  forms, guards, schemas, provider, store hook, tests
        dashboard/             ADMIN overview and role-specific portal
        management/            six typed domain definitions and shared management engine
        directory/             permitted faculty listing
        profile/               auth/me and own academic metadata
      services/                typed client, resources and client tests
      types/api.ts
      styles/index.css
      test/                    setup, fixtures and provider helper
    package.json, package-lock.json
    vite.config.ts, tsconfig.json, eslint.config.js, playwright.config.ts
    index.html, .env.example, .gitignore, .prettierrc.json, .prettierignore
    node_modules/, dist/, .artifacts/, test-results/    ignored generated output
```

Recommend a separate future remote named **CAMPUSFLOW-FRONTEND**. This phase creates no repository/remotes/commits.
Future Phase5 is Faculty Finder + Smart Timetable with backend/frontend work together; it has not started.

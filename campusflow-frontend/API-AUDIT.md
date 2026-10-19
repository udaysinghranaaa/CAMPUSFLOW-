# CampusFlow Phase 4.5 — backend contract audit (before implementation)

Read-only audit: controllers, all DTOs, service/method authorization, SecurityConfiguration,
SecurityErrors, GlobalExceptionHandler, AuthService, OwnProfileService, PasswordPolicy,
application.properties, V2 SQL and PHASE4.md. Backend baseline: 151ec999f0a5cb049a43dfd17cadde1b9cb4698d; clean worktree.
The attached brief reports Phase 4 including cleanup verified locally; this agent has no DB/login credentials.

## Shared transport and error shape

JSON bodies, bearer Authorization header; no response wrapper. POST creation 201 + Location,
PUT/PATCH 200, DELETE 204. Lists return {content,page,size,totalElements,totalPages,last}.
page is zero based; size 1..100 (default20); sort fixed by backend id.
ApiError: {timestamp,status,error,message,path,fieldErrors:Record<string,string>}.
400 invalid inputs, 401 missing/invalid authentication, 403 forbidden, 404 absent, 409 conflict/dependencies,
5xx server error. Never surface raw diagnostics or log credential-bearing traffic.

## Authentication

POST /api/v1/auth/login {email,password} -> {tokenType,accessToken,expiresIn,refreshToken}.
POST /api/v1/auth/refresh {refreshToken} -> same; rotation consumes the submitted token.
POST /api/v1/auth/logout {refreshToken} ->204 (bearer required).
POST /api/v1/auth/change-password {currentPassword,newPassword} ->204; revokes sessions/access version.
GET /api/v1/auth/me -> {account,student:null|Student,faculty:null|Faculty}; server selects principal-owned profile.
Account: id,email,role(ADMIN|FACULTY|STUDENT),enabled,studentId,facultyId,createdAt,updatedAt.
Login password nonblank <=72 UTF-8 bytes. New/initial password >=12 characters, <=72 UTF-8 bytes.
Refresh opaque43 chars, JSON only. No cookies; CORS localhost:5173, allowCredentials=false.

## Academic resources (/api/v1)

- departments: GET list/get shared authenticated; POST/PUT/DELETE ADMIN. Filter code (exact normalized), page,size.
  Request name<=120,code<=20,description nullable<=1000. Response adds id,createdAt,updatedAt.
- faculty: all ADMIN. GET list/get, POST,PUT,DELETE, PATCH /{id}/status {active:boolean}.
  Filters name<=150,departmentId,active,page,size.
  Request fullName<=150,email<=254,employeeCode<=30,designation nullable<=100,departmentId.
  Response adds id,departmentCode,active,createdAt,updatedAt.
- students: all ADMIN. GET list/get,POST,PUT, PATCH /{id}/status {status}.
  NO DELETE. Status ACTIVE|INACTIVE|GRADUATED|SUSPENDED. Enrollment immutable on PUT.
  Filters name,enrollmentNumber,departmentId,batchId,sectionId,semester,status,page,size.
  Request enrollmentNumber<=40,rollNumber<=30,fullName<=150,email<=254,phoneNumber nullable<=25
  (characters +0-9() .-),departmentId,batchId,sectionId nullable,currentSemester>0,admissionYear1900..9999.
  Response adds id,status,createdAt,updatedAt. Batch must belong to department; section to batch.
- batches: GET list/get shared authenticated; POST/PUT/DELETE ADMIN. Filter departmentId,page,size.
  Request name<=150,startYear/endYear1900..9999,endYear>startYear,departmentId,active.
  Response adds id,createdAt,updatedAt.
- sections: GET list/get shared authenticated; POST/PUT/DELETE ADMIN. Filter batchId,page,size.
  Request name<=30,batchId,capacity nullable>0,active. Response adds id,createdAt,updatedAt.
- admin/accounts: ADMIN GET list/get,POST provision,PATCH /{id}/status {enabled}.
  Provision {email,initialPassword,role:STUDENT|FACULTY,studentId|null,facultyId|null}.
  Exactly the matching profile link required. No ADMIN creation, no account delete/update API.
  Own ADMIN status change rejected. Response Account; never password hash/tokens.
- directory/faculty: authenticated GET page,size ONLY. Reduced fields
  {id,fullName,designation,departmentCode,active}; no private email/employeeCode/student data.
  GET /api/campus/health public -> {status,application,message}.

## Frontend decisions

Vite React SPA at sibling campusflow-frontend. ADMIN six management pages + real-count dashboard;
FACULTY/STUDENT own-profile via auth/me only + reduced directory. Shared authenticated profile/password.
Paginated relationship selectors (no silently truncated first100 options), dependent department/batch/section
selection; enroll immutable in edit. Real request DTOs serialized explicitly, no response metadata in writes.
No student delete or ADMIN provisioning. Confirm deletion/status changes; backend remains authority.

## Session limitation and narrow future proposal

Keep BOTH tokens solely in module memory; deduplicate refresh, no automatic retries of refresh POST,
clear all private cache on logout/expiry/user change. Reload has no persisted credential: restore gate
explicitly resolves anonymous and asks login again. No localStorage/sessionStorage/IndexedDB token writes.
A token-free preference may remember theme. Do not claim reload-persistent login works.
No backend changes in this phase.
For approved future persistence: server-set HttpOnly cookie (Secure in production, SameSite=Lax for
same-site deployment; SameSite=None+Secure only when cross-site required), narrowly scoped path and
explicit Max-Age/expiry; refresh/logout cookie transport, rotation replaces cookie, logout/password
clears cookie, refresh responses omit raw refresh value. Require Origin checks + CSRF token on
cookie-authenticated state-changing endpoints (including refresh/logout/login as applicable), exact
credentialed CORS origins and frontend credentials:include. Test replay, CSRF, cookie deletion/path,
production TLS, concurrent tabs and expiry before enabling. Approval required before changing backend.

# CampusFlow Phase 4 — Security, JWT and RBAC

## Status and read-only audit

**Implemented; not complete until real PostgreSQL and live API verification pass locally.** The available build has passed 118 unit/web tests. Database password access is intentionally unavailable to the agent. V3, persisted locking/rotation, bootstrap and live authentication are implemented but unverified here. Expected local totals are 119 regular tests (including contextLoads) and 38 integration tests (24 existing plus 14 security tests); expectations are not execution evidence.

The audit found a clean main branch, Java release 21, Boot 4.1.1, existing DTO/service/repository architecture and passing historical Phase 3 reports (78 regular, 24 integration). Existing endpoints, tests, scripts, V1/V2 and configuration were inspected before edits. No previous work was discarded. The dependencies resolved to Spring Security 7.1.1 through Boot's dependency management; no security-library version is independently pinned.

Unchanged migration SHA-256 values:

- V1: ABFD84268D7CDD20784DFCEBB2FC58E6D41EEAD34177C68F60504DFCCCF5BBB2
- V2: EBC3B6C40AB2E76FD68C140A5D737BF0334BFA1350F345420B4723A207E49B11

## 1. Security design and explicit policies

Use Spring Security's maintained OAuth2 Resource Server/Jose JWT facilities with Nimbus, rather than a handwritten bearer-token filter. This application issues its own development login tokens; it does not claim to implement a complete OAuth authorization server.

- BCrypt cost 12; passwords require at least 12 Java characters and at most 72 UTF-8 bytes. Passwords are never persisted in plaintext or returned. Unknown-email, wrong-password and disabled-account login errors share one generic 401 response.
- HS256 only, with a Base64 environment key containing at least 32 cryptographically random bytes. Required key has no default. Missing/invalid key fails startup. Issuer and audience are checked; timestamps require iat and exp with 30 seconds of clock skew. Access lifetime defaults to 900 seconds and is constrained to 60..900 seconds.
- Claims: stable numeric account ID in sub, role, security version ver, iat, exp, iss, aud and random jti. No email, academic record or password goes into JWT payloads.
- Each authenticated bearer request loads current account state and compares enabled, role and security version. Disabling/re-enabling and password changes increment version. Old access tokens cannot regain validity after reactivation.
- Refresh credentials are 32 SecureRandom bytes encoded as 43-character unpadded Base64URL. PostgreSQL stores only SHA-256 hex hashes, not raw credentials. Lifetime defaults to seven days and is configurable from one to thirty days.
- Every session mutation first obtains the same account PESSIMISTIC_WRITE lock. Refresh finds the account ID by hash, locks the account, then reads the token row. This avoids stale token state while a concurrent refresh waits.
- Rotation revokes the old row and inserts a fresh hashed credential atomically. Reuse of a known revoked token revokes ALL refresh sessions and increments access-token version for the account. The transaction deliberately commits those defensive effects while returning 401. There is no retry grace window.
- Concurrent refresh: one request may issue a successor; the losing duplicate is treated as replay and revokes that successor too. Clients must serialize refresh and use fresh login after this condition.
- Logout revokes the supplied refresh credential only if owned by the authenticated account. Normal logout does not immediately invalidate already-issued access JWTs; they retain the short remaining lifetime. Replaying that revoked credential subsequently triggers the strict account-wide replay policy.
- Password change verifies the current password, stores a new BCrypt hash, revokes every refresh session and invalidates every old access token, including the caller's. Log in again afterward.

### Login email and academic profiles

Login email is an independent, normalized account identity. Changing Student.email or Faculty.email does not change login email, re-link an account or trigger password recovery. Provisioning links by stable profile ID, not by trusting a matching email. This prevents a contact-email edit from taking over login identity. No login-email editing or public recovery endpoint is implemented.

Academic status/active and account enabled are separate policies. Suspending an academic record does not implicitly modify login state; ADMIN must explicitly disable the account to block authentication. Account disable never deletes academic records.

## 2. PostgreSQL V3 schema

user_accounts: bigint identity id, normalized unique email varchar(254), BCrypt password_hash varchar(100), role varchar(20), enabled boolean, optional unique faculty_id/student_id FKs, security_version bigint, password_changed_at and inherited audit timestamps.

The role/profile CHECK permits exactly:

- ADMIN: neither profile link.
- FACULTY: faculty link required, student link absent.
- STUDENT: student link required, faculty link absent.

NOT NULL role plus this CHECK excludes unknown roles. Each profile can link to only one account. Foreign keys use RESTRICT, not cascade. Password hashes must match BCrypt's stored format; email must be lowercase/trimmed/nonblank. Application DTOs additionally validate email syntax.

refresh_tokens: bigint identity id, required account FK, unique 64-character token_hash, expires_at, optional revoked_at and audit timestamps. CHECKs require lowercase SHA-256 hexadecimal and expiry after creation. Indexes cover account and expiry lookups. Account deletion is restricted while session rows exist; there is no public account-delete route.

V3 is additive. V1/V2 are never edited, schemas are never reset and Hibernate remains ddl-auto=validate. Refresh-token rows are retained for replay detection; a future retention job should remove only sufficiently old expired records under an explicit policy.

## 3. Authorization matrix — every existing API family

| Route/method | Anonymous | ADMIN | FACULTY | STUDENT |
| --- | --- | --- | --- | --- |
| GET /api/campus/health | Allow | Allow | Allow | Allow |
| POST /api/v1/auth/login, /refresh | Allow credentials flow | Allow | Allow | Allow |
| GET /api/v1/auth/me | 401 | Own account | Own linked faculty | Own linked student |
| POST /api/v1/auth/logout, /change-password | 401 | Own account | Own account | Own account |
| GET /api/v1/departments and /{id} | 401 | Allow | Allow | Allow |
| GET /api/v1/batches and /{id} | 401 | Allow | Allow | Allow |
| GET /api/v1/sections and /{id} | 401 | Allow | Allow | Allow |
| GET /api/v1/directory/faculty | 401 | Reduced directory | Reduced directory | Reduced directory |
| Existing GET /api/v1/faculty and /{id} | 401 | Full administrative DTO | 403 | 403 |
| Existing GET /api/v1/students and /{id} | 401 | Full administrative DTO | 403 | 403 |
| All Department/Faculty/Student/Batch/Section writes, including status/deletes where implemented | 401 | Allow | 403 | 403 |
| /api/v1/admin/accounts management | 401 | Allow | 403 | 403 |
| Unknown/unlisted routes | Denied | Denied | Denied | Denied |

The reduced directory returns only id, fullName, designation, departmentCode and active, and lists active faculty. It excludes email, employeeCode, timestamps and account data. Existing Faculty administrative responses are preserved for ADMIN.

Faculty access to private student records is conservatively denied: there is no verified teaching-assignment scope yet. Granting every faculty member every student's email/phone/history would be excessive. Own profile access is through /auth/me; no student/faculty self-edit fields are authorized in this phase. There are no unnecessary own-profile mutation routes.

Existing business services gain method-security defense. Metadata read methods override ADMIN-only service rules with authenticated access. OwnProfileService, logout and password change check accountId against authentication.principal.id at the service layer, including when invoked outside the controller. Even ADMIN cannot use the own-profile service to request another account ID.

## 4. Endpoints and safe examples

### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{"email":"user@example.edu","password":"<private-password>"}
```

```json
{"tokenType":"Bearer","accessToken":"<redacted>","expiresIn":900,"refreshToken":"<redacted>"}
```

200 with Cache-Control: no-store. Do not attach an expired bearer token to login/refresh; omit Authorization for those public credential flows. No public registration or ADMIN role-selection signup exists.

### Refresh, logout and password

```http
POST /api/v1/auth/refresh

{"refreshToken":"<private-refresh-token>"}
```

200 with a new TokenResponse; atomically replace BOTH access and refresh values in the development client. Old refresh reuse returns 401 and revokes the account's sessions.

```http
POST /api/v1/auth/logout
Authorization: Bearer <access-token>

{"refreshToken":"<private-refresh-token>"}
```

204; another account's refresh credential is rejected. Logout is idempotent for an already-revoked credential owned by the caller, subject to a still-valid access token.

```http
POST /api/v1/auth/change-password
Authorization: Bearer <access-token>

{"currentPassword":"<private-current>","newPassword":"<private-new>"}
```

204; all previous access/refresh credentials become unusable. New-password validation is also checked in the service, including UTF-8 byte length.

GET /api/v1/auth/me returns an OwnProfileResponse:

```json
{"account":{"id":5,"email":"login@example.edu","role":"STUDENT","enabled":true,"studentId":4,"facultyId":null,"createdAt":"<instant>","updatedAt":"<instant>"},"student":{"id":4,"enrollmentNumber":"<own-enrollment>","rollNumber":"<own-roll>","fullName":"<own-name>","email":"<academic-contact>","phoneNumber":null,"departmentId":1,"batchId":2,"sectionId":3,"currentSemester":1,"admissionYear":2023,"status":"ACTIVE","createdAt":"<instant>","updatedAt":"<instant>"},"faculty":null}
```

Faculty receives its own full FacultyResponse instead of student. ADMIN has both profile fields null. Password hashes, token hashes and security version are never public response fields.

### ADMIN provisioning and management

```http
POST /api/v1/admin/accounts
Authorization: Bearer <admin-access>

{"email":"login-student@example.edu","initialPassword":"<private-initial-secret>","role":"STUDENT","studentId":4}
```

FACULTY uses role FACULTY with facultyId instead. STUDENT/faculty link mismatches yield 400; missing profile 404; duplicate email or already-linked profile 409. ADMIN provisioning through this route is rejected.

201 plus Location /api/v1/admin/accounts/{id}; response is AccountResponse shown above with no password/hash. GET collection supports page>=0 and size=1..100; GET /{id} returns AccountResponse. PATCH /{id}/status with {"enabled":false} or true returns 200, revokes sessions and increments version. ADMIN cannot change their own enabled state (409).

Initial secret delivery: the administrator supplies a strong random initial password privately and transmits it through a trusted separate channel, never an application log or API response. The recipient should promptly call change-password. Forced first-login change, forgotten-password recovery and email delivery are not implemented; do not pretend an initial password is automatically single-use.

### Error contract

401 means missing/invalid credentials or JWT; 403 means authenticated but not permitted. Filter and method/controller failures use ApiError fields timestamp, status, error, message, path and fieldErrors. Login errors are generic and do not expose whether an email exists. 400/404/409 behavior for valid ADMIN academic requests remains unchanged. Unexpected 500 errors never return SQL, stack traces or keys.

## 5. Secure bootstrap and local verification

Bootstrap is a separate manual, non-web application invocation, never a public HTTP route. It applies/validates migrations using the existing campusflow_app database role; no PostgreSQL roles/permissions are changed. An advisory transaction lock serializes bootstrap attempts. If any ADMIN account already exists, including disabled ones, bootstrap refuses; it never overwrites an existing account. Ordinary provisioning cannot create ADMIN.

First administrator only:

```powershell
Set-Location C:\CAMPUSFLOW\campusflow-backend
.\scripts\Bootstrap-Admin.ps1
```

Prompts privately for the database and initial ADMIN password, asks for an email, builds the JAR, and invokes Java with -Dspring.main.web-application-type=none and --campusflow.bootstrap-admin=true. The runner checks that explicit non-web JVM setting before creating anything and terminates only its own CLI process after a committed success. There is no default password. If ADMIN exists already, use that account instead of rerunning bootstrap.

Full verification with an existing ADMIN:

```powershell
.\scripts\Verify-Phase4.ps1
```

Privately supply DB_PASSWORD and the existing ADMIN login password. The script generates an ephemeral signing key if JWT_SECRET is absent; alternatively it accepts the process-scoped JWT_SECRET you supplied. It never prints the key or tokens. It builds/runs regular and PostgreSQL tests, starts a random-port loopback server and logs in as the existing administrator. It checks Phase 1-3 APIs, provisions only its own temporary linked STUDENT/FACULTY accounts, exercises auth/refresh/replay/logout/password/status/role boundaries and removes only its own captured fixtures. The existing administrator's password and enabled state are not modified. The verification admin session is logged out afterward.

The same script prints actual totals from Surefire/Failsafe report attributes after a successful run. It refuses to continue to live checks if the suite fails. Old Verify-Phase2.ps1 and Verify-Phase3.ps1 remain functional with shared helper additions: they now privately authenticate an existing ADMIN against their temporary server and add bearer headers. They also get a temporary key if needed. Integration security is never disabled to run those workflows.

Common scripts snapshot/restore their JWT/token process environment. Verify-Phase4 also retains Phase 3's database-environment restoration. Bootstrap restores all values it changes. No User/Machine environment secret is created. Optional -MavenPath and -JavaPath allow explicit executables.

Manual process-only setup for a normal server launch:

```powershell
$secret = Read-Host 'Database password' -AsSecureString
try { $env:DB_PASSWORD = [Net.NetworkCredential]::new('', $secret).Password } finally { $secret.Dispose() }
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes); $env:JWT_SECRET = [Convert]::ToBase64String($bytes) } finally { $rng.Dispose() }
# Keep this same private terminal for a development launch:
java -jar target/campusflow-backend-0.0.1-SNAPSHOT.jar
# After stopping it, clear secrets if they were created for this terminal:
Remove-Item Env:DB_PASSWORD,Env:JWT_SECRET -ErrorAction SilentlyContinue
```

To privately supply a pre-existing Base64 signing key instead of generating one:

```powershell
$secret = Read-Host 'Base64 JWT signing key' -AsSecureString
try { $env:JWT_SECRET = [Net.NetworkCredential]::new('', $secret).Password } finally { $secret.Dispose() }
```

Never put passwords/keys into command-line arguments, tracked properties, shell output or pasted screenshots. Do not enable transcripts, MVC request-body dumps, Hibernate bind logging or bearer-header diagnostics around credential flows. Authentication DTO toString methods are redacted. MVC/security diagnostic logging is limited, MockMvc print is none and database constraint/bind diagnostics are suppressed to avoid leaking hashes from failed rows. Real errors return safe ApiError values.

### Deployment and rotation

HS256 is appropriate for this single trusted backend. Deploy with TLS and a secret manager/injected environment; a length check cannot prove a human-supplied key has sufficient entropy. All instances must share the same protected key and issuer/audience. This phase has one key, not a kid-based overlapping key ring. Changing it invalidates existing access tokens immediately; legitimate refresh credentials can obtain newly signed access tokens. Coordinate changes across instances. A compromised-key incident additionally requires an audited account/session invalidation response. No incident-wide destructive SQL is executed automatically.

## 6. Request flows and Hinglish learning guide

```text
Login:
Client JSON credentials
 -> AuthController (@Valid)
 -> AuthService account lock
 -> AuthenticationManager / DaoAuthenticationProvider
 -> AccountDetailsService + PasswordEncoder
 -> BCrypt verification against UserAccount
 -> hashed refresh row + signed JWT
 -> redacted-in-logs TokenResponse over HTTPS

Protected API:
Authorization: Bearer JWT
 -> Spring Security BearerTokenAuthenticationFilter
 -> NimbusJwtDecoder (HS256, signature, time, issuer, audience)
 -> AccountJwtConverter (enabled, role, version from PostgreSQL)
 -> SecurityFilterChain URL policy
 -> @PreAuthorize method policy / ownership
 -> existing academic Service -> Repository -> PostgreSQL -> DTO JSON

Refresh:
Opaque credential -> SHA-256 lookup -> owning account row lock
 -> read current token state
 -> valid: revoke old + insert successor in one transaction
 -> replay: revoke ALL account sessions + increment version, commit, then 401
```

1. **Authentication vs authorization:** Authentication poochta hai user kaun hai; authorization decide karta hai woh kya kar sakta hai. Express mein password/JWT middleware aur role/ownership middleware alag responsibilities hain.
2. **Session vs JWT:** Session cookie server-side login state identify karti hai. JWT signed claims carry karta hai. Yahan HTTP session nahi banta; account lookup immediate revocation ke liye deliberate DB dependency hai.
3. **JWT structure:** Header.algorithm + payload.claims + signature. Base64 encoding encryption nahi hai; payload readable hai. Isliye private student data/password token mein nahi daalte.
4. **Access vs refresh:** Access short-lived API credential hai. Refresh high-entropy opaque credential hai jo sirf next session credentials lene ke liye hai. Refresh raw value DB mein nahi hoti.
5. **BCrypt:** Node bcrypt/bcryptjs jaisi adaptive salted password hashing. encode() new salt choose karta hai; matches(raw, stored) verify karta hai. Plain SHA-256 human passwords ke liye too fast hai. Random 256-bit refresh secrets ke SHA-256 hashes ka threat model alag hai.
6. **SecurityFilterChain:** Express app.use(...) middleware chain jaisa, lekin standard Spring filters request authenticate/authorize karte hain. No deprecated WebSecurityConfigurerAdapter or custom token-parsing filter is used.
7. **AuthenticationManager:** Login authentication delegate karta hai. DaoAuthenticationProvider UserDetailsService se user aur PasswordEncoder se hash check karata hai. Controller khud password compare nahi karta.
8. **UserDetailsService and authorities:** AccountDetailsService login email se identity load karta hai. ROLE_ADMIN, ROLE_FACULTY, ROLE_STUDENT authorities framework role checks ke liye hain. JWT role DB role se match hona chahiye.
9. **@PreAuthorize:** Service call se pehle expression evaluate hota hai. Role-only checks ownership guarantee nahi karte; own-profile/password/logout methods principal.id equality bhi check karte hain.
10. **401 vs 403:** Valid authentication absent/invalid ho toh 401. Login ho gaya lekin requested operation allowed nahi ho toh 403. API clients ko HTML login page nahi milti.
11. **CSRF vs CORS:** CSRF browser ke automatically attached ambient credentials exploit karta hai. CORS browser cross-origin response policy hai, authentication replacement nahi. Current bearer headers and explicit JSON refresh transport ke liye CSRF disabled hai; exact React development origin is configured, without credentialed wildcard CORS.
12. **Future frontend storage:** Current JSON credential transport is for controlled development clients. Future browser refresh should use HttpOnly Secure SameSite cookies with a deliberate CSRF and credentialed-CORS design. Access tokens preferably stay in memory. localStorage is not an ideal secure credential store. Cookies are not implemented prematurely here.
13. **Expiration/rotation/logout:** JWT exp bounds access lifetime; 30s clock tolerance applies. Rotation consumes an old refresh credential. Logout revokes its session credential; replay and password/status changes have stronger account-wide invalidation effects.
14. **RBAC vs ownership:** STUDENT role alone does not permit reading another Student ID. /auth/me derives the ID from trusted account linkage; service checks the caller rather than trusting request parameters.
15. **Database integrity:** UNIQUE and CHECK/FK constraints are final boundaries even when two requests race. Pessimistic account locking serializes session mutations. Refresh replay revocation intentionally commits despite a 401; rolling that defensive transaction back would leave stolen sessions valid.

## 7. Executed tests, intended database coverage and limits

Executed commands include read-only git/source/report/config audits, approved Maven dependency resolution, compilation and complete default verify builds. Exact final build:

```powershell
& 'C:\Users\udays\.m2\wrapper\dists\apache-maven-3.10.0\2aaba761688b1a6ea5a46e7e5f4d5406ed7a75ee8da95e5e3079863a23461c9b\bin\mvn.cmd' '-Dmaven.repo.local=C:\CAMPUSFLOW\campusflow-backend\target\maven-repository' -B verify
```

Output is captured in target/phase4-build-final.log. The completed default run passed 118 tests: existing 77 plus AccountProvisioningTests=7, AuthServiceTests=12, OwnershipTests=2, SecurityBoundaryTests=9, JwtSecurityTests=11. No default test failed or was skipped. contextLoads remains in the unchanged opt-in database group; it is not counted as passed in the 118.

An initial JWT expiry fixture incorrectly set iat after exp; that run failed one test. The fixture now has valid chronological claims and the final expiry test verifies actual decoder rejection. No failing test was removed or disabled.

New SecurityPostgreSqlIT contains 14 intended real-database tests: V3/hash validation, actual login/me, generic credential errors, rotation/hash persistence, replay revocation commit, concurrent refresh, logout ownership, password invalidation, disable/re-enable, role/ownership boundaries, role/profile CHECK, duplicate email/profile link, protected academic access and persisted refresh expiry. These tests use committed owned fixtures to exercise real locks; cleanup uses captured IDs only. Existing database tests keep rollback fixtures and gain explicit ADMIN test identities. Both actual filters and method security stay enabled.

| Verification | Actual status |
| --- | --- |
| Compilation/executable JAR | Passed |
| Default unit/web tests | 118 passed |
| Real JWT signature/algorithm/time/issuer/audience tests | Passed |
| Role/ownership and existing academic web regressions | Passed with filters enabled |
| PowerShell syntax | All scripts parsed successfully |
| V1/V2 checksums | Unchanged |
| V3 and new full-context Hibernate mappings | Pending local PostgreSQL execution |
| All 38 PostgreSQL tests | Compiled, not executed by agent |
| Bootstrap command and live Phase 1-4 API checks | Implemented; local execution pending |
| Testcontainers | No success claimed; existing local PostgreSQL strategy retained |

Current warnings: Mockito's dynamic-agent/class-sharing notices under JDK 26 and optional Maven repository metadata/network notices; these did not fail the successful builds. The inherited Maven wrapper error is not refactored. Scripts find Maven on PATH or the verified cached Maven 3.10.0 distribution.

### Remaining risks before deployment

- Mandatory real PostgreSQL/bootstrap/live verification remains blocked by deliberate private credential isolation.
- No distributed login rate limiting, MFA, password-recovery flow, forced first-login password change or dedicated security audit/event pipeline yet. Production deployment needs a rate-limited TLS boundary and appropriate monitoring.
- Generic login responses prevent message-based enumeration, not a claim of perfect timing equivalence across every account state.
- JWT validation needs current account state in PostgreSQL; availability and query overhead are explicit tradeoffs for immediate disable/version checks.
- Normal logout access tokens remain valid for their short remaining lifetime; strict replay can log out legitimate parallel clients. This is documented behavior, not an invisible grace mechanism.
- Refresh retention cleanup and overlapping signing-key rotation are not automated.
- Academic contact changes do not update login identity; administrators must understand this separation.
- Faculty private student reads and self-profile edits remain denied until a safe assignment/field policy is approved.
- No frontend, cookies, localStorage integration, Kafka/Redis/Docker requirements, microservices or Phase 4.5 work is included.

## 8. Exact created and modified files

All paths are relative to campusflow-backend. Modified existing files:

| Path | Reason |
| --- | --- |
| pom.xml | Boot-managed security/resource-server and security-test starters; existing versions preserved. |
| scripts/Verify-Phase2.ps1 | Preserve existing checks with a private ADMIN login, bearer headers and security-environment restoration. |
| scripts/Verify-Phase3.ps1 | Preserve Phase 3 checks with a private ADMIN login, bearer headers and restored key/session environment. |
| src/main/java/com/example/campusflow_backend/exception/GlobalExceptionHandler.java | Safe 400/401/403 mappings for validation and Spring Security exceptions. |
| src/main/java/com/example/campusflow_backend/service/AcademicBatchService.java | Method-security ADMIN writes and authenticated metadata reads. |
| src/main/java/com/example/campusflow_backend/service/AcademicSectionService.java | Method-security ADMIN writes and authenticated metadata reads. |
| src/main/java/com/example/campusflow_backend/service/DepartmentService.java | Method-security ADMIN writes and authenticated metadata reads. |
| src/main/java/com/example/campusflow_backend/service/FacultyService.java | ADMIN-only full faculty operations; directory uses a separate minimized service. |
| src/main/java/com/example/campusflow_backend/service/StudentService.java | ADMIN-only private student operations; own profile uses explicit ownership service. |
| src/main/resources/application.properties | Required JWT environment settings, lifetimes, explicit CORS and credential-safe logging. |
| src/test/java/com/example/campusflow_backend/CampusflowBackendApplicationTests.java | Keep full database context test; add ephemeral security key and explicit ADMIN test identity. |
| src/test/java/com/example/campusflow_backend/controller/AcademicControllerTests.java | Keep academic assertions; import security and use explicit ADMIN identity. |
| src/test/java/com/example/campusflow_backend/controller/BusinessControllerTests.java | Keep Phase 2 assertions; import security and use explicit ADMIN identity. |
| src/test/java/com/example/campusflow_backend/controller/CampusControllerTests.java | Keep public health assertions with the actual filter configuration enabled. |
| src/test/java/com/example/campusflow_backend/integration/AcademicPostgreSqlIT.java | Keep every V2 constraint/CRUD test; add ephemeral key and explicit ADMIN identity. |
| src/test/java/com/example/campusflow_backend/integration/PostgreSqlIT.java | Keep every V1 search/FK/CRUD test; add ephemeral key and explicit ADMIN identity. |

Created files:

| Path | Purpose |
| --- | --- |
| PHASE4.md | Design, runbook, learning guide, exact inventory and honest verification report. |
| scripts/Bootstrap-Admin.ps1 | Explicit guarded non-web administrative bootstrap using private process-only inputs. |
| scripts/Cleanup-Phase4Account.sql | Delete only captured verification account ID/email/profile pairs and their session rows. |
| scripts/Verification-Security.ps1 | Shared ephemeral-key, private login, session logout and environment snapshot/restore helpers. |
| scripts/Verify-Phase4.ps1 | Run database suites, real auth/RBAC and prior academic APIs; report actual totals and cleanup own fixtures. |
| src/main/java/com/example/campusflow_backend/auth/AccountResponse.java | Account output mapper with no hash, token or security-version fields. |
| src/main/java/com/example/campusflow_backend/auth/AccountService.java | ADMIN provisioning, read/paging and status mutations with session invalidation. |
| src/main/java/com/example/campusflow_backend/auth/AccountStatusRequest.java | Required enabled flag for administrative status updates. |
| src/main/java/com/example/campusflow_backend/auth/AuthService.java | Standard-provider login; locked rotation/replay/logout/password transaction boundaries. |
| src/main/java/com/example/campusflow_backend/auth/BootstrapAdminRunner.java | Opt-in non-web-only CLI runner; terminates its own process after successful bootstrap. |
| src/main/java/com/example/campusflow_backend/auth/BootstrapAdminService.java | Advisory-locked one-time ADMIN creation that refuses existing administrators. |
| src/main/java/com/example/campusflow_backend/auth/FacultyDirectoryResponse.java | Reduced directory DTO excluding private email and employee code. |
| src/main/java/com/example/campusflow_backend/auth/FacultyDirectoryService.java | Authenticated active-faculty directory mapping within a read transaction. |
| src/main/java/com/example/campusflow_backend/auth/LoginRequest.java | Normalized email and password validation with redacted diagnostics. |
| src/main/java/com/example/campusflow_backend/auth/OwnProfileResponse.java | Own account plus linked Student or Faculty DTO. |
| src/main/java/com/example/campusflow_backend/auth/OwnProfileService.java | Service-level principal-ID ownership check and own-profile mapping. |
| src/main/java/com/example/campusflow_backend/auth/PasswordChangeRequest.java | Current/new password input with redacted diagnostics. |
| src/main/java/com/example/campusflow_backend/auth/PasswordPolicy.java | Service-enforced minimum length and BCrypt UTF-8 byte limit. |
| src/main/java/com/example/campusflow_backend/auth/ProvisionAccountRequest.java | Normalized ADMIN-provisioning input with redacted initial-password diagnostics. |
| src/main/java/com/example/campusflow_backend/auth/RefreshRequest.java | Bounded opaque refresh input with redacted diagnostics. |
| src/main/java/com/example/campusflow_backend/auth/RefreshTokenCodec.java | SecureRandom opaque credential generation and SHA-256 hashing. |
| src/main/java/com/example/campusflow_backend/auth/TokenResponse.java | Access/refresh development response; toString redacts credentials. |
| src/main/java/com/example/campusflow_backend/controller/AccountController.java | ADMIN account provisioning/list/detail/status HTTP routes. |
| src/main/java/com/example/campusflow_backend/controller/AuthController.java | Login, refresh, me, logout and password-change routes. |
| src/main/java/com/example/campusflow_backend/controller/FacultyDirectoryController.java | Authenticated bounded-paging minimized-directory route. |
| src/main/java/com/example/campusflow_backend/entity/AccountRole.java | Closed ADMIN/FACULTY/STUDENT enum. |
| src/main/java/com/example/campusflow_backend/entity/RefreshToken.java | Hashed credential, account relationship, expiry and revocation entity. |
| src/main/java/com/example/campusflow_backend/entity/UserAccount.java | BCrypt account identity, unique profile links and version invalidation entity. |
| src/main/java/com/example/campusflow_backend/exception/InvalidRequestException.java | Explicit safe client-input failure, mapped to 400. |
| src/main/java/com/example/campusflow_backend/repository/RefreshTokenRepository.java | Hash lookup, account-ID projection and account-wide revocation. |
| src/main/java/com/example/campusflow_backend/repository/UserAccountRepository.java | Account lookup/uniqueness and PESSIMISTIC_WRITE lock queries. |
| src/main/java/com/example/campusflow_backend/security/AccountDetailsService.java | UserDetailsService backed by normalized login identity and enabled state. |
| src/main/java/com/example/campusflow_backend/security/AccountJwtConverter.java | Current DB enabled/role/version validation and safe authenticated principal conversion. |
| src/main/java/com/example/campusflow_backend/security/AccountPrincipal.java | Only stable account ID, role and version; no password/token field. |
| src/main/java/com/example/campusflow_backend/security/IdentityConfiguration.java | BCrypt cost 12, Clock and DaoAuthenticationProvider/AuthenticationManager beans. |
| src/main/java/com/example/campusflow_backend/security/JwtConfiguration.java | Required decoded key; fixed HS256 and timestamp/issuer/audience validators. |
| src/main/java/com/example/campusflow_backend/security/JwtService.java | Minimal short-lived JWT issuance with explicit signing algorithm. |
| src/main/java/com/example/campusflow_backend/security/SecurityConfiguration.java | Fail-closed URL policy, method security, stateless bearer flow and explicit CORS. |
| src/main/java/com/example/campusflow_backend/security/SecurityErrors.java | Consistent filter-level JSON 401/403 errors without credential details. |
| src/main/resources/db/migration/V3__create_accounts_and_refresh_tokens.sql | Additive account/session tables with role-link/hash/unique/FK constraints and indexes. |
| src/test/java/com/example/campusflow_backend/auth/AccountProvisioningTests.java | Seven provisioning, byte-policy/status and bootstrap-guard tests. |
| src/test/java/com/example/campusflow_backend/auth/AuthServiceTests.java | Twelve login, rotation, replay, logout, password and redaction unit tests. |
| src/test/java/com/example/campusflow_backend/auth/OwnershipTests.java | Two actual method-security tests, including ADMIN denied another own-profile ID. |
| src/test/java/com/example/campusflow_backend/controller/SecurityBoundaryTests.java | Nine filter-enabled role/CORS/error-boundary web tests. |
| src/test/java/com/example/campusflow_backend/integration/SecurityPostgreSqlIT.java | Fourteen persisted security/locking/rotation tests with exact owned-fixture cleanup. |
| src/test/java/com/example/campusflow_backend/security/JwtSecurityTests.java | Eleven real encoder/decoder, tampering, algorithm, claims and account-state tests. |
| src/test/java/com/example/campusflow_backend/support/SecurityIntegrationSupport.java | Ephemeral generated test key via DynamicPropertySource; no filter bypass. |
| src/test/java/com/example/campusflow_backend/support/SecurityWebSupport.java | Real filter chain with mocked decoder/account converter for focused MVC role tests. |

Git status at report time: branch main, 16 modified existing files and 48 new untracked files. No commit, push, reset or discarded work. V1/V2 remain unchanged.

## 9. Interview preparation — 27 questions with answers

1. **Authentication versus authorization?** Authentication establishes identity; authorization checks permitted actions. A valid student JWT must still fail an ADMIN write.
2. **Is a JWT encrypted?** Normally no. Its Base64URL header/payload are readable; the signature provides integrity/authenticity, not secrecy.
3. **Why separate access and refresh tokens?** Short access lifetime limits exposure while refresh provides a controlled, revocable reauthentication mechanism.
4. **Why opaque refresh values instead of another long-lived JWT?** Random opaque credentials have no trusted client-visible claims and can be centrally revoked/rotated by hash lookup.
5. **Why SHA-256 for refresh but BCrypt for passwords?** CSPRNG refresh values have high entropy; human passwords need a slow adaptive salted function to resist guessing.
6. **HS256 versus RS256?** HS256 uses one shared secret to sign/verify; RS256 uses private signing and public verification keys. This single backend chooses HS256; distributed verification often benefits from asymmetric keys.
7. **What do iss and aud protect?** They prevent accepting a valid signature from the wrong issuer or for the wrong service/client context.
8. **Why pin the signing algorithm?** A token-controlled algorithm must not select arbitrary verification behavior. This decoder explicitly permits HS256 and rejects none/HS512.
9. **Can logout revoke a self-contained JWT?** Not inherently. This implementation bounds normal logout access by expiry and uses DB version checks for stronger account-wide invalidation events.
10. **Can stateless authentication still query a database?** Yes. Stateless means no server HTTP session is needed; a current-account lookup supplies revocation state and an availability/query-cost tradeoff.
11. **What is SecurityFilterChain?** The configured servlet security pipeline for CORS, bearer authentication, exception handling, sessions and URL authorization.
12. **What is AuthenticationManager?** An authentication coordinator; ProviderManager delegates to providers such as DaoAuthenticationProvider for username/password verification.
13. **What does UserDetailsService do?** Loads login identity, hash, enabled state and authorities. It does not independently verify the password.
14. **hasRole versus hasAuthority?** hasRole('ADMIN') conventionally checks ROLE_ADMIN; hasAuthority checks the literal authority string.
15. **Why method security as well as URL rules?** It protects service operations and ownership when invoked from another controller or future entry point. Proxy/self-invocation limitations still need understanding.
16. **Why not authorize by submitted user ID?** IDs in URLs/bodies are attacker-controlled. Compare to trusted principal ID and use server-owned profile linkage.
17. **401 versus 403?** 401 requires valid authentication; 403 means the established identity lacks permission. Neither should return SQL or credential diagnostics.
18. **CSRF versus CORS?** CSRF abuses automatic credential attachment; CORS constrains browser cross-origin access. Neither is a replacement for authentication or ownership checks.
19. **Why disable CSRF now but revisit cookies?** Current credentials are explicit bearer headers/JSON body secrets, not ambient cookies. Cookie-backed refresh adds a different CSRF threat model.
20. **Why does BCrypt use a salt and cost?** Unique salts defeat precomputed reuse; cost slows each guess. Its 72-byte input boundary requires byte-aware validation.
21. **Why lock the account before refresh rows?** A consistent account-first order serializes rotation/password/logout/status operations and avoids both double issuance and lock-order deadlocks.
22. **Why read the refresh row after acquiring the lock?** Reading it before waiting can leave stale managed state. The second request must observe the first request's committed revocation.
23. **Why commit replay defense while returning 401?** If the exception rolled the transaction back, successors would remain valid. noRollbackFor is narrow and deliberate for the expected credential rejection.
24. **Why block public role-based signup?** Allowing client-selected ADMIN or arbitrary profile links would create privilege escalation and account takeover paths.
25. **What protects role/profile consistency?** Service checks provide clear input errors; SQL UNIQUE/FK/CHECK constraints protect races and non-API writes.
26. **What do mock web tests prove versus PostgreSQL tests?** Mock identities prove filter/method/HTTP boundaries; real persisted tests are needed for migrations, account state, transaction commits, locks and replay/concurrency semantics.
27. **What must key rotation account for?** Shared deployment state, verifier overlap if needed, old access invalidation, refresh policy and an incident response. A one-key implementation must coordinate replacement rather than pretending it supports a key ring.

## Primary references

- [Spring Security JWT resource-server support](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Boot security/resource-server starter conventions](https://docs.spring.io/spring-boot/reference/using/build-systems.html)
- [Spring Security NimbusJwtDecoder](https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.html)

Phase 4 is **implemented but not fully verified**. Do not start the planned Phase 4.5 React/TypeScript work until the local security workflow passes and the user approves the next milestone.

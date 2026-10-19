# CampusFlow Phase 2 implementation report

> Follow-up: the first real PostgreSQL run exposed nullable-search parameter binding and a managed-entity FK test setup issue. See [PHASE2-INTEGRATION-FIX.md](PHASE2-INTEGRATION-FIX.md) for the corrections, latest build evidence and required local database rerun. The original report below records the initial implementation.

Phase 2 code is implemented. The executed default Maven build passed 35 unit/web tests.
Actual PostgreSQL migrations, Hibernate schema validation, full application startup, and persistent HTTP CRUD remain pending local execution because DB_PASSWORD is intentionally available only in the user's terminal. Docker was checked and its daemon is unavailable; Testcontainers coverage is blocked, not passed.

## 1. Files created and modified

All paths below are relative to campusflow-backend. Existing main application, CampusController, CampusService, CampusHealthResponse, and CampusServiceTests are unchanged.

Modified files:

| File | Purpose |
| --- | --- |
| pom.xml | Add Boot-managed JPA, Flyway starter, PostgreSQL Flyway module, PostgreSQL JDBC runtime driver, validation; separate default tests from the postgres-it profile. Preserve Boot 4.1.1 and Java target 21. |
| src/main/resources/application.properties | DB_* variables, local connection defaults, required password, Hibernate validate, Flyway authority with clean disabled, Open Session in View disabled, safe fallback errors. |
| src/test/java/com/example/campusflow_backend/CampusflowBackendApplicationTests.java | Preserve contextLoads; tag it database because a complete application context now requires PostgreSQL. It runs under postgres-it, not in the default test count. |
| src/test/java/com/example/campusflow_backend/controller/CampusControllerTests.java | Preserve every existing health assertion; use WebMvcTest plus the real CampusService so the health contract can be tested without a database. |

Created production Java files under src/main/java/com/example/campusflow_backend/:

| File | Purpose |
| --- | --- |
| entity/AuditedEntity.java | Shared creation/update timestamps through JPA callbacks. |
| entity/Department.java | Department table mapping and controlled updates. |
| entity/Faculty.java | Faculty mapping, active flag, lazy required department association. |
| repository/DepartmentRepository.java | JPA CRUD, paging, department-code lookup and duplicate checks. |
| repository/FacultyRepository.java | JPA CRUD, uniqueness/reference checks, parameterized combined department/status/name search. |
| service/DepartmentService.java | Transactional creation, listing, detail, updates and guarded deletion. |
| service/FacultyService.java | Transactional creation, combined filtering, detail, updates, status changes and deletion. |
| controller/DepartmentController.java | Versioned department routes, validated input, HTTP statuses and Location header. |
| controller/FacultyController.java | Versioned faculty routes, validated input, HTTP statuses and Location header. |
| dto/DepartmentRequest.java | Validated department input; trims fields and uppercases codes. |
| dto/DepartmentResponse.java | Department output without exposing the entity. |
| dto/FacultyRequest.java | Validated faculty input; trims fields, lowercases email, uppercases employee code. |
| dto/FacultyResponse.java | Faculty output with department ID/code and timestamps. |
| dto/FacultyStatusRequest.java | Requires an explicit non-null Boolean status. |
| dto/PageResponse.java | Stable content/page/size/totalElements/totalPages/last response contract. |
| dto/ApiError.java | Timestamp, status, error, safe message, path and fieldErrors contract. |
| exception/ResourceNotFoundException.java | Explicit missing-record failure. |
| exception/ConflictException.java | Explicit duplicate or relationship conflict. |
| exception/GlobalExceptionHandler.java | Maps exceptions to consistent 400/404/409/500 responses; handles 405/406/415 too. Never returns exception internals. |

Other created files:

| File | Purpose |
| --- | --- |
| src/main/resources/db/migration/V1__create_departments_and_faculty.sql | Creates both tables, constraints and indexes. No reset, drop, seed or baseline operation. |
| src/test/java/com/example/campusflow_backend/service/DepartmentServiceTests.java | 8 Mockito tests for creation, duplicates, lookup, update and deletion. |
| src/test/java/com/example/campusflow_backend/service/FacultyServiceTests.java | 10 Mockito tests for creation, missing department, duplicate identifiers, filters, wildcard escaping, update, status and deletion. |
| src/test/java/com/example/campusflow_backend/controller/BusinessControllerTests.java | 15 MVC tests for API contracts, validation, bounds, errors and writes. |
| src/test/java/com/example/campusflow_backend/integration/PostgreSqlIT.java | 8 opt-in real-PostgreSQL tests for Flyway history, schema-backed persistence, unique/FK constraints, repository queries, service changes and HTTP handling through MockMvc. |
| scripts/Test-DatabaseConnection.ps1 | Private password prompt; connection-only check using process environment variables. Restores prior password environment values. |
| scripts/Verify-Phase2.ps1 | Private prompt; runs connection check and postgres-it suite; starts packaged server on an automatically chosen loopback port; exercises live CRUD, error cases and health; cleans up only its own rows and server. |
| PHASE2.md | This report, API reference, setup, testing evidence and interview preparation. |

## 2. Final relevant architecture

```text
campusflow-backend/
  pom.xml
  mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties
  PHASE2.md
  scripts/
    Test-DatabaseConnection.ps1
    Verify-Phase2.ps1
  src/main/java/com/example/campusflow_backend/
    CampusflowBackendApplication.java
    controller/
      CampusController.java
      DepartmentController.java
      FacultyController.java
    service/
      CampusService.java
      DepartmentService.java
      FacultyService.java
    repository/
      DepartmentRepository.java
      FacultyRepository.java
    entity/
      AuditedEntity.java
      Department.java
      Faculty.java
    dto/
      CampusHealthResponse.java
      DepartmentRequest.java
      DepartmentResponse.java
      FacultyRequest.java
      FacultyResponse.java
      FacultyStatusRequest.java
      PageResponse.java
      ApiError.java
    exception/
      ConflictException.java
      ResourceNotFoundException.java
      GlobalExceptionHandler.java
  src/main/resources/
    application.properties
    db/migration/V1__create_departments_and_faculty.sql
  src/test/java/com/example/campusflow_backend/
    CampusflowBackendApplicationTests.java
    controller/
      CampusControllerTests.java
      BusinessControllerTests.java
    service/
      CampusServiceTests.java
      DepartmentServiceTests.java
      FacultyServiceTests.java
    integration/
      PostgreSqlIT.java
  target/                         # ignored generated build/cache/logs
```

## 3. Database design

`departments`: bigint identity primary key `id`; required `name` varchar(120) and unique `code` varchar(20); optional `description` varchar(1000); required created_at and updated_at timestamptz.

`faculty`: bigint identity primary key `id`; required `full_name` varchar(150), unique `email` varchar(254), unique `employee_code` varchar(30); optional `designation` varchar(100); required department_id bigint foreign key; required active boolean (default true); required created_at and updated_at timestamptz.

Many faculty can reference one department. No bidirectional collection or cascading department deletion is configured. The foreign key uses ON DELETE RESTRICT. The service returns 409 when any faculty reference a department, including inactive faculty. Reassign or explicitly delete those faculty before deleting the department.

Database primary-key/unique constraints create their own indexes. Additional indexes cover faculty.department_id and faculty.active. Nonblank database checks supplement request validation. Case-insensitive substring name search is supported, but a conventional B-tree will not accelerate a leading-wildcard search; no misleading name index is added.

Application inputs normalize department/employee codes to uppercase and emails to lowercase before saving. Database unique constraints enforce uniqueness of stored values and protect concurrent requests. A precheck improves error clarity but does not replace database enforcement.

Flyway runs V1 on startup. Hibernate uses ddl-auto=validate and never creates/updates tables. Flyway clean is disabled. If a pre-existing nonempty schema has no Flyway history, startup should fail safely; inspect it before deciding how to adopt migrations. Do not reset it or edit an applied migration. Future changes need a new versioned migration.

## 4. Configuration and secure local verification

Defaults: DB_HOST=localhost, DB_PORT=5432, DB_NAME=campusflow_db, DB_USERNAME=campusflow_app. DB_PASSWORD has no default and is required for startup.

PowerShell process-only environment setup for a manual launch:

```powershell
Set-Location C:\CAMPUSFLOW\campusflow-backend
$env:DB_HOST = 'localhost'
$env:DB_PORT = '5432'
$env:DB_NAME = 'campusflow_db'
$env:DB_USERNAME = 'campusflow_app'
$secret = Read-Host 'CampusFlow database password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
Remove-Variable secret
```

Do not echo DB_PASSWORD, dump the environment, or pass passwords on command lines. These commands do not write User/Machine environment variables or tracked files. Child Java/Maven processes inherit the process environment.

Connection-only check (also prompts privately if needed):

```powershell
.\scripts\Test-DatabaseConnection.ps1
```

Complete verification (also prompts privately if needed):

```powershell
.\scripts\Verify-Phase2.ps1
```

The full script finds Maven on PATH or the cached 3.10.0 distribution and uses target/maven-repository. Optional -MavenPath and -JavaPath accept explicit executable paths. It runs Maven verify -Ppostgres-it, then starts the packaged JAR on a random loopback port, so it does not stop the existing server on 8080. It tests real HTTP CRUD, duplicate/validation/not-found/conflict responses, filtering, status updates and the original health response. Only records created by the script are removed afterward. The server it started is stopped in finally. Cleanup failures identify only the temporary record IDs.

The database integration tests roll back their own data at the end of each test. Flyway's schema migration persists, and PostgreSQL identity sequences may advance even when inserts roll back. Existing rows are not cleared or overwritten. Do not run these tests against production.

When finished with manual environment setup:

```powershell
Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
```

The scripts themselves restore whichever password values existed before they ran.

## 5. API reference and examples

Examples below describe the implemented contract; the example IDs/timestamps are illustrative, not claimed live results.

| Method | Path | Successful response |
| --- | --- | --- |
| GET | /api/campus/health | 200 original Phase 1 JSON |
| POST | /api/v1/departments | 201 DepartmentResponse, Location header |
| GET | /api/v1/departments | 200 PageResponse |
| GET | /api/v1/departments/{id} | 200 DepartmentResponse |
| PUT | /api/v1/departments/{id} | 200 DepartmentResponse |
| DELETE | /api/v1/departments/{id} | 204, empty body |
| POST | /api/v1/faculty | 201 FacultyResponse, Location header |
| GET | /api/v1/faculty | 200 PageResponse |
| GET | /api/v1/faculty/{id} | 200 FacultyResponse |
| PUT | /api/v1/faculty/{id} | 200 FacultyResponse |
| PATCH | /api/v1/faculty/{id}/status | 200 FacultyResponse |
| DELETE | /api/v1/faculty/{id} | 204, empty body |

POST /api/v1/departments, Content-Type: application/json:

```json
{"name":"Computer Science","code":"CSE","description":"Engineering department"}
```

Response (201; Location: /api/v1/departments/1):

```json
{"id":1,"name":"Computer Science","code":"CSE","description":"Engineering department","createdAt":"2026-10-18T15:00:00Z","updatedAt":"2026-10-18T15:00:00Z"}
```

GET /api/v1/departments/1 returns the same response shape. PUT /api/v1/departments/1 accepts the same fields as POST, for example:

```json
{"name":"Computer Science and Engineering","code":"CSE","description":"Updated description"}
```

The response is 200 with the updated name/description, existing id/createdAt and latest updatedAt. PUT replaces the editable fields; omitted optional fields become null. DELETE /api/v1/departments/1 returns 204 if no faculty reference it, 409 otherwise, or 404 if missing.

POST /api/v1/faculty:

```json
{"fullName":"Asha Sharma","email":"asha@example.edu","employeeCode":"EMP001","designation":"Assistant Professor","departmentId":1}
```

Response (201; Location: /api/v1/faculty/2):

```json
{"id":2,"fullName":"Asha Sharma","email":"asha@example.edu","employeeCode":"EMP001","designation":"Assistant Professor","departmentId":1,"departmentCode":"CSE","active":true,"createdAt":"2026-10-18T15:00:00Z","updatedAt":"2026-10-18T15:00:00Z"}
```

GET /api/v1/faculty/2 returns this shape. PUT /api/v1/faculty/2 accepts the same fields as faculty POST, for example:

```json
{"fullName":"Asha Sharma","email":"asha@example.edu","employeeCode":"EMP001","designation":"Professor","departmentId":1}
```

It returns 200 with the updated designation and timestamp. PUT preserves the active status. To change that status, PATCH /api/v1/faculty/2/status:

```json
{"active":false}
```

Response: 200 FacultyResponse with active=false. DELETE /api/v1/faculty/2 returns 204 with no body; subsequent detail requests return 404.

List/search parameters:

- GET /api/v1/departments?code=cse&page=0&size=20 : exact normalized code match.
- GET /api/v1/faculty?departmentId=1&active=true&name=asha&page=0&size=20 : combine any or all optional filters.
- Without filters, list all records with pagination. Pages start at 0, default size is 20, maximum size is 100. Ordering is stable by ascending id.
- Name search is case-insensitive substring matching. Percent, underscore and the escape character are treated literally.

Example paged faculty response:

```json
{"content":[{"id":2,"fullName":"Asha Sharma","email":"asha@example.edu","employeeCode":"EMP001","designation":"Professor","departmentId":1,"departmentCode":"CSE","active":true,"createdAt":"2026-10-18T15:00:00Z","updatedAt":"2026-10-18T15:00:00Z"}],"page":0,"size":20,"totalElements":1,"totalPages":1,"last":true}
```

Departments use the same page envelope with DepartmentResponse entries. An empty result has content=[], totalElements=0 and totalPages=0.

Invalid requests return 400, missing records/referenced departments return 404, duplicate codes/emails/employee codes and referenced department deletions return 409. Unexpected errors return a safe 500 without SQL, stack traces or internal exception messages.

Example 400:

```json
{"timestamp":"2026-10-18T15:00:00Z","status":400,"error":"Bad Request","message":"Request validation failed.","path":"/api/v1/faculty","fieldErrors":{"email":"must be a well-formed email address"}}
```

Example 409:

```json
{"timestamp":"2026-10-18T15:00:00Z","status":409,"error":"Conflict","message":"Department code already exists.","path":"/api/v1/departments","fieldErrors":{}}
```

## 6. Request flow

Client -> Controller -> Service -> Repository -> Hibernate -> PostgreSQL -> response DTO -> Jackson -> HTTP JSON.

For faculty creation, Spring first binds JSON to FacultyRequest and runs Bean Validation. FacultyController delegates to FacultyService. The transactional service loads the department, checks identifiers and constructs Faculty. Spring Data supplies the repository implementation. Hibernate generates parameterized SQL and persists through JDBC. PostgreSQL enforces the unique/FK constraints. After flushing, the service maps the entity into FacultyResponse while the transaction is open. The controller returns 201 with a Location header. If anything fails, the exception handler produces the appropriate safe response.

open-in-view=false means database work and lazy relationship access stay within the service transaction. Faculty list queries fetch department details with EntityGraph, avoiding one extra department query per faculty response.

## 7. Beginner Hinglish and MERN comparisons

Entity vs Mongoose Model: Entity Java object aur SQL table ka mapping define karti hai. Mongoose schema MongoDB documents ka structure define karta hai; yahan PostgreSQL independently foreign keys aur constraints enforce karta hai.

@Entity: Hibernate ko batata hai ki class persistent entity hai. @Table table ka naam aur mapping metadata define karta hai; actual schema Flyway banata hai.

@Id: primary-key field. @GeneratedValue(IDENTITY): PostgreSQL identity column se ID generate hoti hai, manually ID assign nahi karte.

@Column: Java field ka SQL column name, nullability aur length mapping. @UniqueConstraint schema intent document karta hai; database mein Flyway ki UNIQUE constraint actual guarantee deti hai.

@ManyToOne: kai Faculty rows ek Department ko reference karti hain. @JoinColumn department_id foreign key map karta hai. Mongoose mein ref/populate comparable hai, lekin SQL foreign key missing department aur unsafe deletion ko database level par reject karti hai. LAZY ka matlab related data zarurat par load hota hai. DTO mapping service transaction ke andar hoti hai.

@MappedSuperclass: AuditedEntity ke fields child entity tables mein include hote hain; separate audit table nahi banti. @PrePersist pehli insert se pehle timestamps set karta hai. @PreUpdate update se pehle updatedAt set karta hai. Instant ek point in time hai; PostgreSQL timestamptz same instant store karta hai.

JPA vs Hibernate: JPA persistence ki specification/API hai. Hibernate us specification ka ORM implementation hai. ORM objects aur relational rows ke beech mapping karta hai. Hibernate SQL execute karne ke liye JDBC use karta hai.

JpaRepository vs Mongoose queries: JpaRepository<Entity, Long> extend karne par Spring findById/save/findAll/delete implementation generate karta hai. Yeh Model.findById/create/find queries ke comparable hai. findByCode naam se derived query banti hai; @Query explicit JPQL define karta hai. @Param parameters bind karta hai, input SQL string mein concatenate nahi karte. @EntityGraph selected association ko query ke saath fetch karne ke liye hai.

@Service aur constructor injection: service mein application rules hote hain. Spring constructor ke arguments provide karta hai. Dependencies private final fields mein hain, isliye clearly required hain aur tests mein mocks dena easy hai.

@Transactional: method ke database operations ek transaction mein hote hain. Runtime exception par default rollback hota hai. readOnly=true read methods ke liye hint hai; yeh permissions/security mechanism nahi hai. saveAndFlush pending SQL execute karta hai; flush commit nahi hai. Unique/FK errors response banne se pehle surface ho sakte hain. Database constraints concurrent races ko bhi handle karti hain.

DTO: request/response object ka explicit contract; entity client ko expose nahi hoti. Java record constructor/accessors/equals/hashCode/toString generate karta hai. Is project mein nested JPA objects JSON mein directly return nahi karte. Compact request constructors whitespace/case normalize karte hain; validation uske baad hoti hai.

@RestController: Express router handlers jaisa HTTP entry point. @RequestMapping prefix define karta hai. @PostMapping/@GetMapping/@PutMapping/@PatchMapping/@DeleteMapping HTTP methods map karte hain. @RequestBody JSON bind karta hai. @PathVariable URL ka id read karta hai. @RequestParam query filters/page read karta hai. ResponseEntity status, headers aur body control karta hai.

@Valid: request DTO par Bean Validation run karata hai. @NotBlank required text ko null/blank hone se rokta hai. @Email email format check karta hai, mailbox existence nahi. @Size max text length bound karta hai. @NotNull Boolean false ko allow karta hai lekin missing value reject karta hai. @Positive IDs ko positive rakhta hai. @Min/@Max pagination ko bound karte hain. Explicit ID/query constraints Spring MVC method validation use kar sakti hain; global handler us path ke errors bhi collect karta hai.

@RestControllerAdvice: Express centralized error middleware jaisa. @ExceptionHandler exception type ke hisaab se safe HTTP error return karta hai. SQL errors aur stack traces client ko return nahi hote.

Flyway migration: schema changes ki ordered history, jaise versioned database migration scripts in a Node project. V1 file pehle schema create karti hai. Flyway checksum/history track karta hai. Applied V1 edit karne ke bajay next change V2 mein aayega. ddl-auto=validate mapping check karta hai; migration authority Flyway rehti hai.

Testing: JUnit @Test test method mark karta hai; @BeforeEach fresh fixture banata hai. Mockito @Mock fake dependency provide karta hai; @ExtendWith(MockitoExtension.class) mocks initialize karta hai. @WebMvcTest HTTP/validation/serialization test karta hai, DB nahi. @MockitoBean test Spring context mein service mock register karta hai. @SpringBootTest complete application context load karta hai. @AutoConfigureMockMvc full context ko mock HTTP requests deta hai. Integration-test @Transactional rows rollback karta hai. @Tag("database") explicit database group define karta hai. Maven Surefire default unit/web tests chalata hai; Failsafe opt-in *IT tests chalata hai.

## 8. Executed checks and evidence

Audit commands executed:

```powershell
git status --short
git branch --show-current
rg --files --hidden -g AGENTS.md -g '!target/**' -g '!.git/**'
.\mvnw.cmd -version
docker info --format '{{.ServerVersion}}'
& 'C:\Program Files\PostgreSQL\18\bin\pg_isready.exe' -h localhost -p 5432
```

Results: clean branch main at start; wrapper fails with `Cannot index into a null array`; Docker daemon cannot be reached; PostgreSQL reports accepting connections. No password values were read or printed. No User/Machine credential environment variable was created by the implementation.

Verified fallback:

```powershell
& 'C:\Users\udays\.m2\wrapper\dists\apache-maven-3.10.0\2aaba761688b1a6ea5a46e7e5f4d5406ed7a75ee8da95e5e3079863a23461c9b\bin\mvn.cmd' -version
```

Result: Maven 3.10.0, Java 26.0.1. Maven dependencies initially downloaded with approved network access into the ignored project-local cache.

Executed final build:

```powershell
& 'C:\Users\udays\.m2\wrapper\dists\apache-maven-3.10.0\2aaba761688b1a6ea5a46e7e5f4d5406ed7a75ee8da95e5e3079863a23461c9b\bin\mvn.cmd' '-Dmaven.repo.local=C:\CAMPUSFLOW\campusflow-backend\target\maven-repository' -B verify
```

Build output was redirected to target/phase2-build.log. Results: BUILD SUCCESS; 35 tests, 0 failures, 0 errors, 0 skipped. BusinessControllerTests=15, CampusControllerTests=1, CampusServiceTests=1, DepartmentServiceTests=8, FacultyServiceTests=10. Production code and PostgreSqlIT compiled; executable JAR packaged.

An earlier run failed one web assertion because method-validation errors omitted fieldErrors.active. The handler was corrected; the final 35-test run passed. This was a real failure and rerun, not an ignored test.

PowerShell parser checks passed for both scripts. git diff --check passed (Git emitted line-ending normalization notices).

The original contextLoads database-tagged test was excluded from the default run; it is not part of the 35 passed tests. The 8 PostgreSqlIT methods have not been executed in this agent environment. Testcontainers-based tests are blocked by Docker availability; no Testcontainers test is claimed as passed. User-run script results must be reviewed before marking live database verification complete.

## 9. Limitations and Git safety

- Real PostgreSQL migration success, Hibernate mapping validation, full context startup and live persistent CRUD are pending execution of Verify-Phase2.ps1 in the credential-bearing terminal.
- The existing Maven wrapper problem is preserved and reported; fallback Maven is verified.
- Mockito prints a dynamic-agent warning under JDK 26. It did not fail the final build.
- No authentication/authorization, timetable, booking, messaging, Docker configuration or microservices were introduced.
- APIs are intended for local Phase 2 development; authentication is a later phase.
- No commits, pushes, resets, migration resets, privileged users or database authentication changes were performed.
- git status --short lists four modified tracked files plus the newly created production/test/script/documentation files listed above. No tracked credential file was added.

## 10. Interview preparation

1. What is JPA? A specification for mapping Java objects to relational persistence; Hibernate is one implementation.
2. What does Hibernate do? Maps entities/relationships, tracks managed objects and generates SQL through JDBC.
3. What makes an entity? @Entity, a primary-key mapping, and a JPA-compatible no-argument constructor; this project uses field access.
4. What are primary and foreign keys? A primary key uniquely identifies a row; a foreign key enforces that a reference points to an existing parent row.
5. Why use database UNIQUE constraints when the service checks duplicates? Concurrent requests can both pass the precheck; the database provides the final guarantee.
6. How does ManyToOne work here? Many faculty rows hold department_id references to one department; no reverse collection is required.
7. LAZY versus EAGER? LAZY defers association loading; EAGER requires it to be fetched. Query design and transaction boundaries still matter.
8. What is the N+1 problem? Loading a page followed by one association query per result; EntityGraph fetches department details with the faculty query.
9. What does @Transactional provide? A transaction boundary; runtime exceptions roll back by default. Proxy-based interception means self-invocation needs care.
10. Flush versus commit? Flush sends pending changes to the database; commit finalizes the transaction. Flushed changes can still roll back.
11. Why DTOs? Stable API contracts, explicit validation and control over exposed fields; avoid lazy entity serialization and unintended relationships.
12. Why Flyway plus ddl-auto=validate? Flyway versions schema changes; Hibernate checks mappings without automatically modifying schema.
13. What is a derived repository query? Spring Data interprets method names such as findByCode and generates the query implementation.
14. What does an index improve and cost? It can accelerate qualifying lookups but consumes storage and adds write maintenance; suitability depends on query patterns/selectivity.
15. Why paginate and use HTTP statuses precisely? Pagination bounds result size; 201 means created, 204 means success without content, 400 invalid input, 404 missing, 409 conflict, and 500 unexpected server failure.

## Next phase suggestion

Phase 3 could add authentication and role-based authorization for students, faculty and administrators. It is not implemented. Review real database verification results and approve the next scope first.

## Official references

- Spring Boot database initialization and PostgreSQL Flyway module: https://docs.spring.io/spring-boot/how-to/data-initialization.html
- Spring Boot testing: https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html

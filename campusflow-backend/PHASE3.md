# CampusFlow Phase 3: Student and Academic Management

> Latest test-fixture corrections and verification limits are recorded in section 10. The earlier sections describe the initial implementation.


## Status and audit

**Implemented, but not yet fully verified against PostgreSQL.** Maven BUILD SUCCESS: **77 unit/web tests, zero failures, errors or skipped tests**. Sixteen new PostgreSQL tests compiled but were not executed here because DB_PASSWORD stays in the user's terminal. The existing eight integration tests are preserved.

Read-only audit: actual package com.example.campusflow_backend; Boot 4.1.1; Java target 21; branch main initially clean. Existing record DTOs, AuditedEntity, transactional services, repositories, Criteria search, error handling, scripts and test profiles were inspected. Existing Failsafe summary recorded 8/8 passing, and historical Surefire reports included 36 tests. Those results are Phase 2 evidence, not V2 verification.

V1 SHA-256 remained ABFD84268D7CDD20784DFCEBB2FC58E6D41EEAD34177C68F60504DFCCCF5BBB2. No existing tracked files, POM, dependencies, migrations, production code, tests or Phase 2 scripts were edited. No Phase 4, authentication, frontend or microservices were introduced.

## 1. Every created file

Paths are relative to campusflow-backend. New Java paths below use src/main/java/com/example/campusflow_backend/.

| Java file | Purpose |
| --- | --- |
| entity/AcademicBatch.java | Batch mapping, department, year range, active flag and updates. |
| entity/AcademicSection.java | Section mapping, required batch, optional capacity and active flag. |
| entity/Student.java | Student mapping with immutable enrollment, required department/batch, optional section and status. |
| entity/StudentStatus.java | ACTIVE, INACTIVE, GRADUATED, SUSPENDED enum. |
| dto/BatchRequest.java | Trimmed input, bounded years and cross-field validation. |
| dto/BatchResponse.java | Explicit batch output and mapper. |
| dto/SectionRequest.java | Normalized name, required batch/active and positive capacity validation. |
| dto/SectionResponse.java | Section output and mapper. |
| dto/StudentRequest.java | Normalized, validated creation and full-update input. |
| dto/StudentResponse.java | Student output with association IDs, not entities. |
| dto/StudentStatusRequest.java | Required enum status input. |
| repository/AcademicBatchRepository.java | CRUD, department paging and scoped duplicate checks. |
| repository/AcademicSectionRepository.java | CRUD, batch paging and scoped duplicate checks. |
| repository/StudentRepository.java | Persistence, uniqueness/reference checks and dynamic Criteria filters with fetch graph. |
| service/AcademicBatchService.java | Transactional management and populated-group restrictions. |
| service/AcademicSectionService.java | Transactional management and student-reference restrictions. |
| service/StudentService.java | Assignment validation, immutable enrollment, identifiers, status and mapping. |
| controller/AcademicBatchController.java | Five versioned routes and bounded paging. |
| controller/AcademicSectionController.java | Five versioned routes and bounded paging. |
| controller/StudentController.java | Create/list/detail/update/status routes and validated filters. |

| Other file | Purpose |
| --- | --- |
| src/main/resources/db/migration/V2__create_student_academic_schema.sql | Additive tables, composite FKs, uniqueness, CHECKs, indexes and immutable-enrollment trigger. |
| src/test/java/com/example/campusflow_backend/service/StudentServiceTests.java | 16 student service tests. |
| src/test/java/com/example/campusflow_backend/service/AcademicGroupingServiceTests.java | 12 grouping service tests. |
| src/test/java/com/example/campusflow_backend/controller/AcademicControllerTests.java | 14 MVC contract, validation and error tests. |
| src/test/java/com/example/campusflow_backend/integration/AcademicPostgreSqlIT.java | 16 real PostgreSQL tests with rollback fixtures. |
| scripts/Verify-Phase3.ps1 | Full-suite and live Phase 1-3 verification with private credentials and cleanup. |
| scripts/Cleanup-Phase3Student.sql | Internal cleanup by exact verification student ID and enrollment pair; no application delete route. |
| PHASE3.md | This learning and implementation report. |

## 2. Final relevant folder structure

```text
campusflow-backend/
  pom.xml, mvnw, mvnw.cmd, .mvn/             (unchanged)
  PHASE2.md, PHASE2-INTEGRATION-FIX.md       (unchanged)
  PHASE3.md
  scripts/
    Test-DatabaseConnection.ps1, Verify-Phase2.ps1  (unchanged)
    Verify-Phase3.ps1, Cleanup-Phase3Student.sql
  src/main/java/com/example/campusflow_backend/
    CampusflowBackendApplication.java
    controller/
      CampusController.java, DepartmentController.java, FacultyController.java
      AcademicBatchController.java, AcademicSectionController.java, StudentController.java
    service/
      CampusService.java, DepartmentService.java, FacultyService.java
      AcademicBatchService.java, AcademicSectionService.java, StudentService.java
    repository/
      DepartmentRepository.java, FacultyRepository.java
      AcademicBatchRepository.java, AcademicSectionRepository.java, StudentRepository.java
    entity/
      AuditedEntity.java, Department.java, Faculty.java
      AcademicBatch.java, AcademicSection.java, Student.java, StudentStatus.java
    dto/
      ApiError.java, PageResponse.java, CampusHealthResponse.java
      DepartmentRequest.java, DepartmentResponse.java
      FacultyRequest.java, FacultyResponse.java, FacultyStatusRequest.java
      BatchRequest.java, BatchResponse.java, SectionRequest.java, SectionResponse.java
      StudentRequest.java, StudentResponse.java, StudentStatusRequest.java
    exception/
      ConflictException.java, ResourceNotFoundException.java, GlobalExceptionHandler.java
  src/main/resources/
    application.properties
    db/migration/
      V1__create_departments_and_faculty.sql
      V2__create_student_academic_schema.sql
  src/test/java/com/example/campusflow_backend/
    CampusflowBackendApplicationTests.java
    controller/
      CampusControllerTests.java, BusinessControllerTests.java, AcademicControllerTests.java
    service/
      CampusServiceTests.java, DepartmentServiceTests.java, FacultyServiceTests.java
      StudentServiceTests.java, AcademicGroupingServiceTests.java
    integration/
      PostgreSqlIT.java, AcademicPostgreSqlIT.java
  target/                                  (ignored generated artifacts)
```

## 3. Database design, relationships and policies

Department has many batches; batch has many sections. A student requires department and batch, with section optionally assigned. New IDs are bigint identity keys. Audit timestamps reuse AuditedEntity and TIMESTAMPTZ.

academic_batches: name varchar(150), start_year/end_year integers, department_id FK, active, timestamps. Years are 1900..9999; end_year > start_year. Unique index (department_id, lower(name)) prevents duplicate batch names within a department. UNIQUE(id, department_id) supports the composite student FK.

academic_sections: name varchar(30), batch_id FK, optional positive capacity, active, timestamps. Unique index (batch_id, lower(name)) scopes section names. UNIQUE(id, batch_id) supports the composite student FK.

students: enrollment_number varchar(40), roll_number varchar(30), full_name varchar(150), email varchar(254), optional phone_number varchar(25), department_id, batch_id, optional section_id, positive current_semester, admission_year 1900..9999, status varchar(20), timestamps.

- Enrollment and email are globally unique. Roll is unique within a batch, across sections and unassigned students, but can repeat in another batch.
- Composite FK (batch_id, department_id) -> academic_batches(id, department_id) guarantees matching department.
- Composite FK (section_id, batch_id) -> academic_sections(id, batch_id) guarantees matching batch. Default MATCH SIMPLE permits a null section while the required batch/department FK remains enforced.
- All FK deletions use RESTRICT. Composite assignment references also restrict parent-key updates. No cascade deletion exists.
- Simple JPA ManyToOne ID mappings coexist with additional composite SQL constraints. Java references alone cannot enforce cross-association consistency.
- CHECKs enforce positive semester/capacity, valid year range, allowed status, nonblank identifiers/names and canonical enrollment/roll/email storage.
- API uppercases enrollment/roll/section names, lowercases email and trims input. Enrollment stays immutable through service comparison, JPA updatable=false and a BEFORE UPDATE database trigger.
- Unique indexes also cover their leading-column lookups. Extra indexes support student department, section+batch, semester and status filters. Leading-wildcard name search intentionally has no ineffective ordinary B-tree index.

### Deliberate academic policies

PUT requires the same normalized enrollment and preserves status. PATCH status retains identity/history; no Student DELETE API exists (405). Archived/graduated students still protect groups. Populated batches cannot move departments, and populated sections cannot move batches. A batch with sections cannot be deleted. Student reassignment is allowed only with a valid, consistent grouping combination.

Semester has no invented eight-semester ceiling. Admission year need not equal batch start year, allowing lateral entry. Capacity is positive metadata, not an occupancy/allocation limit. Group active flags preserve addressability for history. Status transitions allow any of the four enum values; no approval workflow is invented.

V2 is additive and does not alter Phase 2 tables or V1. Hibernate remains validate; Flyway owns schema changes. Once V2 is applied, future changes must use V3 rather than editing V2. Existing department deletion returns a safe 409 through the existing handler if new FKs restrict it.

## 4. REST endpoints with sample requests and responses

All example IDs/timestamps are illustrative contracts, not claimed live results. JSON requests use Content-Type: application/json. POST returns 201 plus Location. GET/PUT/PATCH return 200. Successful group DELETE returns 204 with no body.

### Batches

Request B1:

```json
{"name":"B.Tech CSE 2023-2027","startYear":2023,"endYear":2027,"departmentId":1,"active":true}
```

Response B2:

```json
{"id":2,"name":"B.Tech CSE 2023-2027","startYear":2023,"endYear":2027,"departmentId":1,"active":true,"createdAt":"2026-10-18T15:00:00Z","updatedAt":"2026-10-18T15:00:00Z"}
```

| Endpoint | Request example | Response example |
| --- | --- | --- |
| POST /api/v1/batches | B1 | 201 B2; Location /api/v1/batches/2 |
| GET /api/v1/batches?departmentId=1&page=0&size=20 | No body | 200 page envelope containing B2 |
| GET /api/v1/batches/2 | No body | 200 B2 |
| PUT /api/v1/batches/2 | B1 with name changed to CSE Updated | 200 B2 with new name/updatedAt |
| DELETE /api/v1/batches/2 | No body | 204 empty; 409 if students/sections reference it |

### Sections

Request S1:

```json
{"name":"A","batchId":2,"capacity":60,"active":true}
```

Response S2:

```json
{"id":3,"name":"A","batchId":2,"capacity":60,"active":true,"createdAt":"2026-10-18T15:00:00Z","updatedAt":"2026-10-18T15:00:00Z"}
```

| Endpoint | Request example | Response example |
| --- | --- | --- |
| POST /api/v1/sections | S1 | 201 S2; Location /api/v1/sections/3 |
| GET /api/v1/sections?batchId=2&page=0&size=20 | No body | 200 page envelope containing S2 |
| GET /api/v1/sections/3 | No body | 200 S2 |
| PUT /api/v1/sections/3 | S1 with capacity=50 | 200 S2 with new capacity/updatedAt |
| DELETE /api/v1/sections/3 | No body | 204 empty; 409 if students reference it |

capacity can be null. Section name is normalized to uppercase. active is required on group POST/PUT.

### Students

Request T1:

```json
{"enrollmentNumber":"CSE2023001","rollNumber":"001","fullName":"Asha Sharma","email":"asha@example.edu","phoneNumber":"+91 9876543210","departmentId":1,"batchId":2,"sectionId":3,"currentSemester":1,"admissionYear":2023}
```

Response T2:

```json
{"id":4,"enrollmentNumber":"CSE2023001","rollNumber":"001","fullName":"Asha Sharma","email":"asha@example.edu","phoneNumber":"+91 9876543210","departmentId":1,"batchId":2,"sectionId":3,"currentSemester":1,"admissionYear":2023,"status":"ACTIVE","createdAt":"2026-10-18T15:00:00Z","updatedAt":"2026-10-18T15:00:00Z"}
```

| Endpoint | Request example | Response example |
| --- | --- | --- |
| POST /api/v1/students | T1 | 201 T2; Location /api/v1/students/4 |
| GET /api/v1/students?page=0&size=20 | No body | 200 page envelope containing T2 |
| GET /api/v1/students/4 | No body | 200 T2 |
| PUT /api/v1/students/4 | T1 with currentSemester=2, sectionId=null | 200 T2 with those fields/updatedAt changed; enrollment/status preserved |
| PATCH /api/v1/students/4/status | {"status":"GRADUATED"} | 200 T2 with status=GRADUATED and updatedAt changed |

PUT replaces editable fields. Omitted optional phone/section become null. To unassign a section explicitly send sectionId:null. Status changes belong to PATCH. No student hard-delete route is exposed.

Combined filter example:

```text
GET /api/v1/students?name=asha&enrollmentNumber=cse2023001&departmentId=1&batchId=2&sectionId=3&semester=2&status=ACTIVE&page=0&size=20
```

Any subset of seven filters is valid. Name is case-insensitive literal substring. Enrollment is exact after normalization. IDs/semester/status use exact matching. Blank name/enrollment means no filter. Status uses the enum spelling. Pages begin at 0, size defaults to 20 and must be 1..100. Sorting is fixed to ascending id; user sort fields are not exposed.

Every list reuses PageResponse. Empty example:

```json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"last":true}
```

For populated results, content contains the complete response objects above and totals reflect matches. Example populated batch page:

```json
{"content":[{"id":2,"name":"B.Tech CSE 2023-2027","startYear":2023,"endYear":2027,"departmentId":1,"active":true,"createdAt":"2026-10-18T15:00:00Z","updatedAt":"2026-10-18T15:00:00Z"}],"page":0,"size":20,"totalElements":1,"totalPages":1,"last":true}
```

### Errors and preserved APIs

400: invalid DTO/query/enum, malformed JSON, bad year range/capacity. 404: missing student/group/department. 409: duplicate identifier, incompatible grouping, immutable enrollment or restricted deletion. 500: safe unexpected-error message. SQL/stack traces/credentials are never returned.

```json
{"timestamp":"2026-10-18T15:00:00Z","status":409,"error":"Conflict","message":"Batch does not belong to the selected department.","path":"/api/v1/students","fieldErrors":{}}
```

Validation responses include fieldErrors such as email, currentSemester or yearRangeValid. Existing health, Department and Faculty APIs remain unchanged and are covered by regression/local verification.

## 5. Request flow and beginner Hinglish

Client -> Controller -> validated request DTO -> Service transaction -> Repository -> Hibernate/JDBC -> PostgreSQL -> response DTO -> Jackson JSON.

POST Student mein JSON StudentRequest record mein bind hota hai. Compact constructor trim/case normalize karta hai; @Valid constraints validate karti hain. Controller HTTP routing aur 201 Location handle karta hai. Service duplicates/assignments check karke entity banata hai. Repository Hibernate ko delegate karta hai. Database constraints final integrity guarantee deti hain. saveAndFlush se SQL errors response se pehle surface ho sakte hain. Mapper entity ke allowed fields aur association IDs response mein deta hai; entity directly serialize nahi hoti.

### Annotation guide and MERN comparisons

- @Entity: Java class ko persistent table mapping banata hai. Mongoose schema/model ya Prisma model comparable hai; actual relational schema Flyway manage karta hai.
- @Table/@Column: names, lengths/nullability aur update behavior map karte hain. updatable=false Hibernate UPDATE se enrollment omit karta hai. Direct SQL protection ke liye trigger bhi hai.
- @Id/@GeneratedValue(IDENTITY): PostgreSQL numeric identity primary key, MongoDB ObjectId ki jagah.
- @ManyToOne/@JoinColumn: multiple students ek parent ko reference karte hain; JoinColumn ID column batata hai. Mongoose ref/populate comparable hai, lekin SQL FK actual database enforcement hai.
- FetchType.LAZY association zarurat par load karta hai. EntityGraph required to-one associations query ke saath fetch karata hai, avoidable N+1 queries kam karta hai. Optional section optional rehti hai.
- @Enumerated(STRING): enum names store hote hain, positions nahi; reorder safe hai, rename still considered migration maangta hai.
- Existing @MappedSuperclass/@PrePersist/@PreUpdate audit fields inherit/set/update karte hain.
- @Service: application rules ka layer. Constructor injection dependencies explicit rakhti hai aur Mockito tests easy banati hai.
- @Transactional: related writes same transaction mein; runtime exception default rollback. readOnly=true hint hai, permission/security rule nahi. Flush commit nahi hota.
- @RestController and HTTP mapping annotations: Express router/handlers jaisa entry point. @RequestBody JSON, @PathVariable URL ID, @RequestParam query filters read karte hain. ResponseEntity status/header/body control karta hai.
- @Valid/@NotBlank/@Email/@Size/@Positive/@Min/@Max/@NotNull Express plus Zod/Joi validation ke comparable hain. Email format check mailbox existence check nahi. AssertTrue batch year range checks karta hai. JsonIgnore derived validation property ko JSON se exclude karta hai.
- Existing @RestControllerAdvice/@ExceptionHandler Express global error middleware jaisa safe ApiError return karta hai. New exception framework ki zarurat nahi thi.

### Specifications versus dynamic Mongoose filters

MERN mein filter={} banakar supplied fields add karte ho. StudentRepository bhi sirf supplied fields ke Criteria predicates add karta hai. Missing filters nullable function expressions nahi banate. Typed String/Long/Integer/enum paths aur bound values use hote hain; raw SQL concatenate nahi hota. %, _ aur ! escape hote hain, isliye names literal search rehte hain.

### Hibernate versus Prisma/Mongoose

JPA specification/API hai; Hibernate uska ORM implementation hai. Prisma generated typed database access deta hai. Hibernate managed entities, persistence context, lazy loading aur dirty checking provide karta hai. Mongoose MongoDB ke liye ODM hai. PostgreSQL foreign keys/composite constraints enforce karta hai independently of Java validation.

### Transactions and integrity

Duplicate prechecks friendly errors deti hain; concurrent requests dono precheck pass kar sakti hain. Database UNIQUE final guarantee hai. Composite FKs assignment races protect karti hain. No version column is added: concurrent ordinary edits can still be last-write-wins.

Transient entity new/untracked hoti hai, managed entity context mein tracked, detached entity tracking se alag, removed entity deletion ke liye scheduled. Tests fixture flush/clear karte hain taaki SQL FK failure exercise ho, Hibernate's earlier graph validation nahi. RESTRICT errors use 23001; invalid FK insert/update uses 23503. Integration assertions require exact named constraints.

## 6. Secure local verification and cleanup

```powershell
Set-Location C:\CAMPUSFLOW\campusflow-backend
.\scripts\Verify-Phase3.ps1
```

The script privately prompts with Read-Host -AsSecureString, sets DB_PASSWORD only in the process for child Maven/Java and restores previous password/DB_HOST/PORT/NAME/USERNAME process values in finally. It does not write User/Machine environment credentials. Defaults remain localhost:5432, campusflow_db, campusflow_app. Never echo the environment or put passwords on command lines.

Workflow:

1. Reuse Test-DatabaseConnection.ps1.
2. Run postgres-it: all unit/web tests, database context test, eight existing IT tests and sixteen new IT tests.
3. Startup applies/validates Flyway V1/V2 and runs Hibernate schema validation.
4. Start the JAR on a random loopback port, leaving existing servers alone.
5. Exercise preserved health/Department/Faculty APIs and all new CRUD/status/filter/validation/conflict cases.
6. Remove only its own temporary student by both returned ID and unique enrollment through psql quoted variables. Then remove its own section/batch/faculty/department through APIs. No public student-delete route is added.
7. Stop only the temporary server. Cleanup failures report temporary IDs and return failure; no reset fallback exists.

Database tests roll fixture rows back. Migrations persist as intended; identity sequences can advance even after rollback. Existing user data is never cleared. The cleanup SQL has no unbounded DELETE/TRUNCATE. If a later related record prevents cleanup, the script leaves it protected and reports failure.

Unchanged Verify-Phase2.ps1 still works; its existing Maven profile naturally discovers new tests while its live Phase 2 checks stay unchanged. Optional -MavenPath and -JavaPath permit explicit executable paths in both workflows.

Manual process-only password setup for separate commands:

```powershell
$secret = Read-Host 'CampusFlow database password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
Remove-Variable secret
# Run Maven/Java in this terminal. When finished:
Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
```

## 7. Exact executed checks and actual results

Audit used git status --short, git branch --show-current, source/config/script inspection, previous reports and V1 SHA-256 comparison. Initial tracked Git tree was clean.

Executed build, redirected to target/phase3-build-final.log:

```powershell
& 'C:\Users\udays\.m2\wrapper\dists\apache-maven-3.10.0\2aaba761688b1a6ea5a46e7e5f4d5406ed7a75ee8da95e5e3079863a23461c9b\bin\mvn.cmd' '-Dmaven.repo.local=C:\CAMPUSFLOW\campusflow-backend\target\maven-repository' -B verify
```

BUILD SUCCESS: 77 tests, zero failures/errors/skips. New tests: StudentServiceTests=16, AcademicGroupingServiceTests=12, AcademicControllerTests=14. Existing tests contribute 35. Existing database-tagged contextLoads is excluded by the unchanged default test configuration; it is not counted as passed here. All production classes and eleven test source files compile under release 21, including both integration suites. An earlier incremental build passed the original 35 tests before new tests were added; no failures were hidden.

PowerShell Parser.ParseFile passed for Verify-Phase3.ps1. git diff --check passed. V1 hash is identical. Tracked git diff is empty; all Phase 3 files are new/untracked pending review. No dependency/version changes were needed.

| Check | Actual status |
| --- | --- |
| Compilation and executable JAR | Passed |
| Unit/web suite | 77 passed |
| Health/Phase 2 MVC regressions | Passed |
| New sixteen PostgreSQL tests | Compiled, not executed here |
| V2 applied to campusflow_db | Not executed here |
| Hibernate validation of new mappings | Not executed here |
| Live Phase 1-3 APIs | Script syntax checked; local execution pending |
| Old Phase 2 database reports | Historical 8/8 pass, not proof for V2 |
| Testcontainers | No Docker requirement added; no Testcontainers success claimed |

A successful local run should show 78 Surefire tests (77 plus contextLoads), 24 Failsafe tests (8 plus 16), and live API PASS output. These counts are expectations, not claimed executed results.

## 8. Remaining warnings, limitations and technical debt

- Phase 3 remains not fully verified until the private local workflow passes. Mocks do not prove V2, mappings, SQL constraints or live persistence.
- The inherited wrapper PowerShell issue is unchanged. Scripts find Maven on PATH or the previously verified cached 3.10.0 distribution.
- Mockito/Byte Buddy prints JDK 26 dynamic-agent/class-sharing warnings; the build passes.
- No optimistic locking or full event/audit history beyond timestamps and retained status records.
- Capacity is metadata, not concurrency-safe seat allocation.
- Large substring searches may need measured optimization such as pg_trgm later.
- No credentials/authentication/role enforcement is introduced. These are development APIs pending a later authorized security phase.
- Once V2 has been applied, use V3 for subsequent changes rather than editing V2.
- No commit/push, schema reset, data clearing, PostgreSQL permission change or Phase 4 work occurred.

## 9. Interview preparation: 20 questions and explanations

1. **Why separate batch and section tables?** Normalization avoids repeated grouping metadata and makes parent relationships independently manageable/enforceable.
2. **Why store department on Student when batch implies it?** It is an explicit assignment/filter field. A composite FK protects the repeated relationship from becoming inconsistent.
3. **What is a composite foreign key?** Multiple child columns must match one parent key together, preventing unrelated valid IDs from being combined.
4. **Why UNIQUE(id, department_id) when id is already unique?** The referenced composite columns need a matching unique key for PostgreSQL's composite FK.
5. **Why can section be null but batch not null?** A student can join an intake before section allocation. MATCH SIMPLE permits that optional association while the required batch/department constraint remains active.
6. **Why batch-scoped roll uniqueness?** Rolls may repeat across intakes, but cannot collide inside one batch, including different/null sections. Enrollment is a distinct global identifier.
7. **Why prefer status over student deletion?** Status preserves identity and academic history that future attendance/timetable records may reference. Verification fixtures have a separate bounded cleanup path.
8. **JPA versus Hibernate?** JPA defines persistence contracts; Hibernate implements them and issues SQL through JDBC.
9. **What does ManyToOne mean?** Multiple children reference one parent. Fetch policy controls loading; cascade policy separately controls propagated writes/deletes.
10. **What is N+1?** One result query followed by one association query per result. EntityGraph fetches required associations with the main query where appropriate.
11. **Why store enum names?** Names are readable and survive declaration reordering. Renaming a persisted enum still needs a deliberate migration.
12. **What does updatable=false guarantee?** Hibernate omits the column from generated updates. Direct SQL is outside that guarantee, hence the immutability trigger.
13. **Why DTO validation and database constraints?** DTOs provide early friendly errors. Constraints protect concurrent and non-API writes.
14. **Why are duplicate prechecks insufficient?** Concurrent transactions may both see no duplicate. Database UNIQUE is the final decision boundary.
15. **Flush versus commit?** Flush sends SQL within the open transaction. Rollback can undo rows; sequence allocation need not roll back.
16. **How does Transactional work?** Spring proxies method calls to create transaction boundaries. Runtime exceptions roll back by default; self-invocation does not pass through the proxy automatically.
17. **Why dynamic Criteria over nullable JPQL?** Missing filters produce no expressions/bind values; supplied filters use typed predicates, avoiding the earlier ambiguous-null function typing issue.
18. **What does Flyway do?** Applies ordered/checksummed migrations. Hibernate validate checks mappings afterward without silently changing the schema.
19. **Why bounded paging and fixed sorting?** Bounded size protects resources; stable id sorting makes order predictable without exposing arbitrary property paths.
20. **Why test with PostgreSQL?** Mockito cannot prove dialect typing, composite FKs, trigger behavior, SQLSTATEs or migration execution. Real database tests exercise those boundaries.

## References

- [PostgreSQL 18 constraints](https://www.postgresql.org/docs/18/ddl-constraints.html)
- [Spring Data JPA Specifications](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html)

Stop at Phase 3. Review the local verification output before approving later work.

## 10. PostgreSQL fixture corrections — 19 October 2026

Only two project files changed in this correction:

1. src/test/java/com/example/campusflow_backend/integration/AcademicPostgreSqlIT.java
2. PHASE3.md

### Root causes and targeted fixes (Hinglish)

**Literal name search fixture:** Student.update ka parameter order rollNumber, fullName, email, ... hai. Test ne pehle do String arguments ulte diye: `Literal_%! ...` rollNumber mein chala gaya aur original roll name mein. V2 ka ck_student_roll nonblank, trimmed uppercase roll require karta hai. Mixed-case `Literal` us rule ko violate karta tha; yeh search query ki problem nahi thi. Ab existing valid roll unchanged hai aur special characters fullName mein hain. Flush/clear ke baad test persisted roll unchanged assert karta hai, phir literal-name search same student ID verify karti hai. Constraint relax nahi ki gayi. MERN comparison: update object mein fullName ki jagah galti se rollNumber field assign karna jaisa bug; positional Java String arguments mein compiler is swap ko detect nahi karta.

**Year constraint name:** V2 ka unnamed cross-column CHECK end_year ki numeric range aur end_year > start_year enforce karta hai. Prior test ne generated constraint name academic_batches_end_year_check assume kiya. Actual PostgreSQL error metadata in the local Failsafe report identifies academic_batches_check. Ab test pehle pg_constraint/pg_get_constraintdef se isi named CHECK ki definition read karke end_year > start_year verify karta hai, phir the same invalid update par exact SQLSTATE 23514 AND academic_batches_check require karta hai. Any unrelated integrity error still fails. Applied migration rename/edit nahi hui. MERN comparison: validation rule sahi thi; test ne error key/name galat assume kiya tha.

The agent could inspect the migration and captured server constraint diagnostics, but could not query authenticated live metadata because DB_PASSWORD is unavailable here. The added metadata assertion runs during the next local integration execution. No direct live metadata query is claimed as executed in this correction.

### Executed verification

```powershell
& 'C:\Users\udays\.m2\wrapper\dists\apache-maven-3.10.0\2aaba761688b1a6ea5a46e7e5f4d5406ed7a75ee8da95e5e3079863a23461c9b\bin\mvn.cmd' '-Dmaven.repo.local=C:\CAMPUSFLOW\campusflow-backend\target\maven-repository' -B verify
```

Output: target/phase3-fixture-fix-build.log. Actual result: BUILD SUCCESS; **77 tests, 0 failures, 0 errors, 0 skipped**. All integration sources compiled. The existing database context test remains in the unchanged postgres-it profile, hence it is not part of the default 77 count. No test was removed, disabled or newly excluded.

V1 and V2 SHA-256 values before/after remained:

- V1: ABFD84268D7CDD20784DFCEBB2FC58E6D41EEAD34177C68F60504DFCCCF5BBB2
- V2: EBC3B6C40AB2E76FD68C140A5D737BF0334BFA1350F345420B4723A207E49B11

Git diff --check passed. Branch remains main. Existing untracked Phase 3 files are preserved; no commit/push was performed. Only the two files listed above were edited in this correction, apart from ignored generated build output.

The latest user-run results before these fixes were 78/78 regular tests and 22/24 integration tests, with V2 and Hibernate initialization successful. Those are prior local-run results, not a successful rerun of the corrected suite. Corrected PostgreSQL tests and live APIs remain **pending local execution**.

Non-fatal warnings during this build: Mockito dynamic Java-agent/class-sharing warnings and an optional Maven snapshot metadata fetch denied by network permissions. The build still succeeded from cached dependencies.

Secure full local rerun, using the unchanged process-only password workflow:

```powershell
Set-Location C:\CAMPUSFLOW\campusflow-backend
.\scripts\Verify-Phase3.ps1
```

This runs the database-enabled regular suite, all 24 integration tests and, after they pass, live Phase 1-3 APIs. No credentials, migrations, schema constraints or production code were changed. No database reset, existing-data deletion or Phase 4 work occurred.

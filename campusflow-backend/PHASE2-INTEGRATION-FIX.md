# Phase 2 integration failure fix

## Result and scope

Faculty search and the foreign-key integration test have been corrected. The executed Maven build passed all 35 unit/web tests with zero failures, errors or skipped tests. All eight PostgreSqlIT methods remain enabled in the existing postgres-it profile and compile successfully. They have not been rerun here because DB_PASSWORD is intentionally unavailable to this agent. Real database and live HTTP success must be confirmed with the process-only local verification script.

No entity, service, API contract, database migration, credential, database authentication setting or Maven test-selection setting was changed by this fix.

## Exact root causes

### Nullable name parameter in JPQL

The existing Failsafe report identifies two errors:

- httpCrudAndValidationUseRealPersistence: the repository lookup after faculty creation calls search(departmentId, null, null, pageable).
- repositorySearchCombinesFilters: the non-null name lookup precedes a lookup with name=null; that null-name invocation fails.

The old JPQL always included:

```text
(:name is null or lower(f.fullName) like lower(concat('%', :name, '%')) escape '!')
```

The report records SQL containing:

```sql
(? is null or lower(f1_0.full_name) like lower(('%'||?||'%')) escape '!')
```

PostgreSQL reports function lower(bytea) does not exist. The entity/migration map full_name to VARCHAR, so this is not a binary-column mapping mistake. It is the nullable parameter expression: in this failing execution, the right-hand concatenation is resolved as bytea. A declared Java String method parameter did not supply a usable SQL string type throughout that nested expression when the argument was null. The report does not include JDBC bind traces, so it does not establish the exact internal JDBC setNull type code.

The SQL OR null guard does not make an invalid function signature safe. PostgreSQL must resolve expressions and their types before evaluating rows.

### Removed department still referenced by a managed faculty

The old foreignKeyPreventsDepartmentDeletion test saves a department and faculty in one test transaction, then removes the department while the faculty remains managed and still points to it. At flush, Hibernate detects that persistent Faculty references the removed parent and raises TransientPropertyValueException. Spring translates that to InvalidDataAccessApiUsageException, not DataIntegrityViolationException.

The department was originally saved successfully; the message does not mean fixture creation forgot to save it. Removing it changed its lifecycle state while a live managed faculty reference remained. Hibernate's consistency check ran before a database DELETE could exercise the foreign key.

The service's explicit 409 guard remains correct. The migration's ON DELETE RESTRICT remains correct. Neither needed weakening.

## Files changed by this fix

| File | Change |
| --- | --- |
| src/main/java/com/example/campusflow_backend/repository/FacultyRepository.java | Replace nullable JPQL with dynamic, typed Criteria predicates through Specification and JpaSpecificationExecutor. Preserve search signature and department EntityGraph on paged findAll. |
| src/test/java/com/example/campusflow_backend/integration/PostgreSqlIT.java | Flush/clear before FK deletion, reload/delete only the parent, require SQLSTATE 23503. Expand the existing search test to all optional-filter combinations, paging, blank names and literal wildcard characters. Keep all eight tests. |
| scripts/Verify-Phase2.ps1 | Add live checks for no filters, department-only, active-only, name-only and pagination. Use a unique token in temporary faculty names to avoid matching existing user data. Keep the private password flow and existing cleanup rules. |
| PHASE2.md | Link the follow-up correction from the original implementation report. |
| PHASE2-INTEGRATION-FIX.md | This evidence and explanation report. |

FacultyService was inspected and preserved: it already trims names, maps blanks to no filter and escapes LIKE special characters. DepartmentService, both entity mappings and V1 are unchanged.

## Before and after query behavior

Before: one fixed SQL shape bound every filter, including absent values, and sent nullable name concatenation through lower().

After: only supplied filters generate predicates. No name filter means no name parameter or name-function expression at all. Present names create lower(full_name) LIKE a non-null String pattern. Java normalizes the pattern with Locale.ROOT; the service retains literal escaping for %, _ and !. Pattern concatenation builds a value, never SQL text. Hibernate binds Criteria values through prepared statements.

- No filters: unrestricted paginated query.
- Department only: department.id equals a Long value.
- Active only: active equals a Boolean value, including false.
- Name only: case-insensitive substring match with a String pattern.
- Combined filters: predicates joined with AND.
- Pagination: Spring Data retains offset/limit, stable caller sort and total counts.
- Department loading: EntityGraph on findAll(Specification, Pageable) preserves eager loading for response mapping without introducing an N+1 query.

The expanded database test covers each case and includes another department to detect accidental filter leakage. It checks an empty next page as well as a populated second page. No-filters and active-only totals are compared against database counts so pre-existing user rows do not break assertions.

## Entity lifecycle and the test correction

- Transient: a new instance that is not persisted/managed.
- Managed: an instance tracked by the current persistence context; changes can be flushed automatically.
- Detached: an instance no longer tracked, although its database row can still exist.
- Removed: a tracked entity scheduled for deletion.

flush() sends pending fixture writes to PostgreSQL but does not commit them. clear() detaches the in-memory graph without deleting rows. The corrected test then deleteById() loads only the parent and flushes its deletion. With no managed faculty graph to trigger Hibernate's earlier association check, PostgreSQL can enforce the FK. The assertion requires DataIntegrityViolationException with SQLSTATE 23503, specifically a foreign-key violation.

The test still uses the original rollback transaction. After the expected SQL error, no further SQL is attempted in that test; teardown rolls the transaction back. No user rows are deleted. Migrations and foreign-key enforcement stay enabled.

## Commands executed and actual results

```powershell
Set-Location C:\CAMPUSFLOW\campusflow-backend
git status --short
git branch --show-current
.\mvnw.cmd -version
```

Branch: main. The workspace already contained uncommitted Phase 2 changes at the start; they were preserved. The wrapper still fails with Cannot index into a null array, so the existing cached Maven 3.10.0 fallback was used.

```powershell
& 'C:\Users\udays\.m2\wrapper\dists\apache-maven-3.10.0\2aaba761688b1a6ea5a46e7e5f4d5406ed7a75ee8da95e5e3079863a23461c9b\bin\mvn.cmd' '-Dmaven.repo.local=C:\CAMPUSFLOW\campusflow-backend\target\maven-repository' -B verify
```

Output was redirected to target/phase2-fix-build.log. Result: BUILD SUCCESS; 35 tests run, 0 failures, 0 errors, 0 skipped. All 23 production source files and seven test source files compiled for Java 21. The JAR was packaged. The original health web test passed.

PowerShell's Parser.ParseFile validated scripts/Verify-Phase2.ps1 successfully. git diff --check passed with existing LF/CRLF normalization notices. Final git status --short --untracked-files=all was inspected.

Evidence from the pre-fix Failsafe report (not a new successful execution): 8 tests, 1 failure, 2 errors; 5 passed. Its captured logs show Flyway validated one migration, public schema was up to date, and Hibernate initialized the EntityManagerFactory. These results establish that the old run reached the real database; they do not prove the corrected tests pass.

Current authenticated verification status:

| Check | Status |
| --- | --- |
| 35 unit/web tests and packaging | Passed in this fix session |
| Eight corrected PostgreSqlIT tests | Compiled; local rerun required |
| Flyway V1 and Hibernate validation | Succeeded in prior user-run report; not rerun here |
| Live Department/Faculty CRUD and health | Local rerun required |
| Testcontainers | Previously unavailable Docker daemon; no success claimed |

The existing database-tagged contextLoads test remains part of the postgres-it run; it is not part of the default 35-test count. No tests were disabled, removed, or newly excluded in this fix.

## Secure local verification command

Run in your own PowerShell:

```powershell
Set-Location C:\CAMPUSFLOW\campusflow-backend
.\scripts\Verify-Phase2.ps1
```

This privately prompts for DB_PASSWORD if missing and keeps it process-only. It checks the PostgreSQL connection, runs Maven -Ppostgres-it verify (the 35 unit/web tests, database context test and all eight integration tests), then starts the built JAR on a temporary loopback port and tests CRUD, filtering, validation, conflicts and health. It stops immediately if the database suite fails. No password is printed or passed on the command line, and previous password environment values are restored in finally.

Database defaults remain localhost:5432/campusflow_db and campusflow_app. The script does not change credentials, reset migrations, drop tables, disable constraints, or clear data. It removes only its own temporary HTTP-created records. Database tests roll back their fixtures; identity sequences can advance normally.

## Beginner Hinglish and MERN comparisons

Old query mein name null hone par bhi SQL mein lower(...name...) expression present thi. Java String declaration se har SQL expression ka database type automatically guaranteed nahi hota. PostgreSQL ne us nullable expression ko binary bytea maana; lower() text ke liye hai. OR guard se SQL type-checking skip nahi hoti.

Ab Express/Mongoose mein conditional filter object banane jaisa approach hai: departmentId diya toh department predicate add karo, active diya toh Boolean predicate add karo, name diya toh text predicate add karo. Jo filter nahi diya, woh query mein aata hi nahi. Criteria API yeh predicates safely banati hai; raw SQL concatenate nahi karte.

Managed entity ko Mongoose document jaisa samajh sakte ho, lekin Hibernate automatically uske changes aur references track karta hai. Parent delete mark kar diya aur managed child ab bhi usko reference kare, toh Hibernate database se pehle object graph reject kar sakta hai. Test mein flush se fixture rows database tak bhejte hain; clear se in-memory tracking detach karte hain. Rows delete nahi hoti. Phir parent delete SQL PostgreSQL tak jaati hai aur actual FK protection check hoti hai.

Business API phir bhi pehle department ke faculty check karke clear 409 deti hai. Database FK concurrent requests ya direct SQL ke against final protection hai.

## Remaining warnings and Git status

Mockito/Byte Buddy emits dynamic Java-agent and class-sharing warnings under JDK 26; these did not fail the build. The wrapper's existing PowerShell error is unchanged. No new dependency/version changes were needed.

The branch remains main. Four tracked files (pom.xml, application.properties and the two Phase 1 test files) were already modified before this fix. The existing Phase 2 source/scripts/report files remain untracked. This fix edited three of those existing untracked code/script files, updated PHASE2.md, and added this report. No commit, push, database mutation or Phase 3 work was performed by the agent during this fix session.

## Primary references

- [Spring Data JPA Specifications](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html)
- [Hibernate 7.4 Session lifecycle and flush](https://docs.hibernate.org/orm/7.4/javadocs/org/hibernate/Session.html)

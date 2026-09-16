# PostgreSQL migration integration tests (SHG-15)

Run from the checkout root with Java 21 and a running **local** Docker engine:

```sh
cd backend
./mvnw verify -Ppostgres-it
```

This runs the existing backend suite, builds the application and runs five
PostgreSQL integration tests through Maven Failsafe's `integration-test` and
`verify` phases. Failures fail the build. Docker being unavailable is an error,
not a skipped test. Plain `./mvnw test` and `./mvnw verify` retain the existing
lightweight/H2 execution path; neither starts Testcontainers. To rerun only the
integration tests during development, including compiling changes:

```sh
./mvnw test-compile failsafe:integration-test failsafe:verify -Ppostgres-it
```

Reports are in `backend/target/failsafe-reports/`; existing backend reports are
in `backend/target/surefire-reports/`. No production access, environment file,
external database, Redis, mail server or JWT keys are required by the new suite.
The migration command's local-mode loopback restriction is preserved, so remote
Docker hosts are not a supported target for this suite.

## Architecture and fixtures

`PostgresMigrationIT` uses JUnit 5 and a single class-scoped Testcontainers
PostgreSQL container. The image is deliberately pinned to
`postgres:16.11-bookworm`, matching the PostgreSQL **16.11** version recorded in
SHG-14's accepted production inventory. No floating `16` or `latest` image is
used. The server version is asserted in every test. Testcontainers is pinned to
1.21.4 rather than Boot 3.5.3's 1.21.2 because the latter failed against local
Docker 29; see the upstream [1.21.4 compatibility release](https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4).
Flyway, Spring Boot and all production dependencies/settings remain unchanged.

Each test gets a unique schema, retaining PostgreSQL search-path behavior used
by the real migration command. All connection settings come from the disposable
container, never the process's `DB_URL` or production configuration. The JUnit
container lifecycle removes the database/container and its schemas, rows and
test role after the class, including assertion failures. Container reuse is
explicitly disabled; Testcontainers' Ryuk cleanup remains enabled. This teardown
does not invoke Flyway `clean` or `repair`. Images remain cached for later runs.

The fixtures contain only invented application rows. The existing SHG-14
accepted catalog is schema metadata, without production rows. Maven copies that
catalog and its read-only inspection query into test resources, providing an
independent expectation without duplicating a large SQL baseline fixture.
The existing-database test executes V1 DDL through JDBC **without Flyway history**
and verifies it against that accepted catalog before recording a baseline.
This proves the accepted production shape; it is not a production dump restore.

Fresh migration, baseline and normal validation/no-op checks invoke the real
`MigrationCommand`. Negative tests use Flyway directly with isolated additional
locations, keeping the production command's fixed-location policy intact.
The failing V3 fixture is outside `db/migration`; checksum testing edits only
JUnit temporary copies of V1/V2. Neither fixture ships in the application JAR.

## Scenarios and assertions

| Test | What it proves |
| --- | --- |
| Fresh database | Empty schema runs V1 and V2; history has exactly two successful SQL rows with expected scripts and non-null checksums; all 86 columns, 46 constraints and 42 indexes match accepted metadata with only the two intended user widths changed to 255; ten tables and nine sequences exist; repeat migration executes zero migrations and leaves history/schema unchanged. |
| Existing accepted baseline | Untracked accepted schema with rows in all ten tables rejects ordinary migrate with exit code 1; explicit baseline at 1 records a successful BASELINE row with a null checksum; V1 never executes as a migration; only V2 executes; every synthetic row survives byte-for-byte in its JSON representation; catalog changes are limited to email/password widths; physical column order and sequence settings/ownership remain unchanged. |
| Controlled failure | A test-only V3 updates users, deletes ledger rows, adds a user column and creates a table, then divides by zero. Flyway throws for V3 with PostgreSQL SQLSTATE 22012. All DDL/DML rolls back; history and every preexisting application row remain unchanged; no V3 success or failed-history row persists. Retrying fails identically. Observed callbacks contain no clean/repair event, and clean remains disabled. |
| Checksum mismatch | V1/V2 run from temporary copies; replacing the already-recorded V2 makes both validate and migrate throw a version-2 checksum mismatch. No replacement SQL executes, no checksum is repaired, and history, data and schema remain intact. |
| Hibernate compatibility | Production entity scanning hydrates populated rows for all ten entity mappings. A restricted application role cannot create schema objects. With production `ddl-auto: none`, Hibernate inserts a UUID user with generated timestamps, updates/reloads both user columns at 255 characters and updates a versioned wallet. Catalog, column order, sequence settings and Flyway history remain unchanged. |

Catalog comparison applies only the same narrow `funds_status_check` rendering
equivalence accepted in SHG-14 and normalizes the isolated schema name to
`public`. It does not relax constraints, indexes, column types or nullability.
Flyway validation alone is not used as evidence of schema equality.

The failure assertions cover ordinary PostgreSQL transactional DDL and DML.
They do not claim rollback of nontransactional operations or sequence-value
advancement. A rolled-back PostgreSQL migration leaves no failed history row;
the previous successful rows remain and the migration is still pending.

Hibernate `validate` remains intentionally disabled in production. This is
runtime mapping compatibility coverage with ORM schema mutation forbidden,
not a claim that annotation-driven DDL exactly matches the accepted catalog.
Timestamp semantics and the fund wallet uniqueness assumption remain later
schema/ORM reconciliation work, as documented in SHG-14.

## Runtime and scope

The suite starts one PostgreSQL container and the normal Ryuk cleanup sidecar,
using random mapped ports and no persistent database volume or external service.
The first run downloads the PostgreSQL image (about 136 MB compressed on the
validated ARM64 host) and any missing Maven dependencies. Later runs use cached
images. On the validated Apple Silicon / Java 21 / Docker 29 host, the cached integration-only
command took **9.5 seconds** including Maven, with **5.5–5.6 seconds** reported for
the five tests including container startup and cleanup. The full
`./mvnw verify -Ppostgres-it` lifecycle took **19.9 seconds**. These are observations,
not performance thresholds; image pulls and host load affect runtime.

Validation on 2026-09-16: **743 existing tests and five PostgreSQL integration
tests passed, with zero failures, errors or skips**. The packaged application
contains V1/V2 but no test fixtures or Testcontainers dependencies. Post-run
Docker inspection found no remaining PostgreSQL test containers or Ryuk sidecars.

SHG-16 and later own deployment orchestration, GitHub Actions integration,
release gates, backup/restore automation, production baseline adoption and
broader schema/ORM reconciliation. None is implemented or executed by SHG-15.

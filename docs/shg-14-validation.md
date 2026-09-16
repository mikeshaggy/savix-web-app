# SHG-14 validation and completion report

Validated on 2026-09-15 in `/Users/shaggy/dev/active/savix-shg-11`, on top of
`c4293c6` (SHG-13), without changing production or starting SHG-15.

## SHG-14 implementation summary

Flyway is the canonical migration mechanism, using both dependencies managed by
Spring Boot 3.5.3 (resolved Flyway 11.7.2). Fresh DBs execute V1 then V2; existing
production requires a verified, manual baseline at version 1 before V2. Web
startup cannot invoke Flyway auto-configuration. Hibernate uses `none` in
production; H2 tests retain their own `create-drop` lifecycle.

## Files changed

- `backend/pom.xml`: BOM-managed Flyway dependencies, explicit normal main class,
  exclusion of legacy resource-root SQL from the packaged JAR.
- `backend/src/main/java/com/mikeshaggy/backend/BackendApplication.java`:
  dispatch migration commands before Spring; exclude Flyway auto-configuration.
- `backend/src/main/java/com/mikeshaggy/backend/migration/MigrationCommand.java`:
  controlled operations, explicit configuration, target/baseline guards and safe output.
- `backend/src/main/resources/db/migration/V1__baseline.sql` and
  `V2__widen_user_columns.sql`: canonical migrations.
- `backend/src/main/resources/application.yml`, `application-prod.yml`, and
  `backend/src/test/resources/application.yml`: explicit schema lifecycle settings.
- `backend/src/test/java/com/mikeshaggy/backend/migration/MigrationCommandTest.java`:
  unit tests for configuration/operation restrictions and startup exclusion.
- `docker-compose.yml`: remove the PostgreSQL legacy init-script mount.
- `README.md`, `docs/production-docker.md`, `docs/database-migrations.md`:
  development instructions, migration policy and first-adoption runbook.
- `docs/database/accepted-v1-catalog.json`, `inspect-catalog.sql`,
  `compare-catalog.py`: accepted schema metadata and read-only comparison tools.
- `docs/shg-14-validation.md`: this report.

## V1 schema model

Reconstructed from the completed SHG-11 catalog, not from historical SQL filenames:
10 tables, 9 serial/bigserial sequences, 86 columns, 46 constraints and 42 indexes.
The two user fields remain varchar(100). Live names, nullability, defaults,
foreign-key actions, partial uniqueness, physical column order and both ledger
ordering indexes are retained. No data, credentials, production ownership or
sequence current values are copied into V1.

## V2 schema changes

Only `users.email` and `users.password_hash` widen to varchar(255), in one ALTER
TABLE statement. Existing test users and wallets remain byte-for-byte unchanged;
255-character values are accepted afterward. The full post-V2 catalog comparison
shows no other column/constraint/index changes.

## Migration command design

The same executable backend JAR/image accepts `db info|validate|migrate|baseline`.
Dispatch happens before any Spring context, web listener, scheduler or integration
starts. Configuration is explicit; production requires a confirmed host, database
`savix`, schema `public` and PostgreSQL 16. Baseline needs explicit version-1 and
confirmation flags and an existing accepted-shaped schema. Failures exit non-zero;
credentials and raw exception messages are suppressed. Pending files are permitted
during validation; changed checksums and missing/future applied migrations are not.
No routine clean, repair, automatic baseline or arbitrary Flyway overrides exist.

## Production baseline procedure

See [the operator runbook](database-migrations.md). With writes paused: verify the
accepted catalog and absent history, take and restore-test a protected backup,
explicitly baseline at version 1, inspect the single successful BASELINE history
row with null checksum, then validate/migrate V2 and inspect history/data again.
V1 never executes on existing production. No production baseline, migration,
backup, restart or deployment was executed during SHG-14.

## Development initialization changes

Compose initializes the database only; the operator runs the packaged migration
command before starting the backend. There is no competing init SQL mount.
Legacy scripts stay intact at their original paths as historical evidence; none
is scanned or packaged as executable migrations. Existing development volumes
are not erased or automatically baselined.

## Validation performed

| Check | Result |
| --- | --- |
| Maven `verify` on Java 21 | PASS: 743 tests, zero failures/errors/skips |
| Packaged migrations and Flyway dependencies | PASS: V1, V2 and both Flyway 11.7.2 JARs present |
| Legacy SQL packaging | PASS: historical resource-root SQL excluded |
| Command migration discovery | PASS: V1 and V2 visible from executable JAR |
| Fresh PostgreSQL migration | PASS: two successful SQL history rows; V1 then V2 |
| Second migrate | PASS: zero migrations on fresh and adopted DBs |
| V1 catalog comparison | PASS: all 86 columns, 46 constraints and 42 indexes match |
| Sequences | PASS: nine sequences with start/min/increment/cache 1 and no cycle |
| Baseline guards | PASS: empty DB, repeated baseline and version 0 rejected |
| Untracked existing schema | PASS: migrate fails without automatic baseline |
| Explicit baseline adoption | PASS: BASELINE at 1, null checksum, then V2 only |
| Data preservation | PASS: existing user and wallet rows unchanged by V2 |
| Post-V2 catalog | PASS: only the two widths differ; fresh/adopted schemas converge |
| Explicit non-public local schema | PASS: V1 and V2 execute in selected schema |
| Configuration/authentication failures | PASS: non-zero exits, no tested secret leakage |
| Forbidden clean/repair | PASS: rejected before connection |
| Checksum validation | PASS: deliberately changed local history checksum rejected |
| Migration-only isolation | PASS: commands finish with no Spring/web/scheduler startup and no JWT/Redis/mail configuration |
| Normal production-profile web startup | PASS: starts on isolated empty DB, creates no app/history tables even with `SPRING_FLYWAY_ENABLED=true` |
| Git whitespace validation | PASS: `git diff --check` |

Smoke checks used a disposable Docker Desktop PostgreSQL **16.15** container with
tmpfs storage and a random loopback-only port. Production inventory is **16.11**;
the actual production server was never contacted. Both are PostgreSQL 16, but
patch-identical rehearsal against a production restore remains an adoption step.
The comparator recognizes two exact equivalent PostgreSQL renderings of the
fund-status array cast; it does not ignore arbitrary constraint changes.

Temporary web-process keys were generated for this local test and deleted afterward.
The web test process and PostgreSQL container were stopped. Local detailed evidence
and the one-off smoke driver remain ignored under `.local/shg-14/`; Maven output is
`.local/shg-14-maven.log`. The runtime Docker image was not rebuilt in this task;
validation used the packaged JAR reached by the existing image entrypoint.

## Known schema assumptions intentionally left unchanged

- Timestamp columns remain without time zone despite existing Instant mappings.
- `funds.fund_wallet_id` does not gain an ORM-implied uniqueness constraint.
- Live index/constraint names, absent indexes and additional ledger index remain.
- No index optimization, privilege/role change or schema cleanup is included.
- Hibernate `validate` is not enabled; SQL catalog comparison is authoritative.
- Normal web startup itself is not a migration/readiness gate; later deployment
  orchestration must require a successful explicit migration before replacement.

## Items deferred to SHG-15 / later subtasks

Full Testcontainers coverage, release/CI automation, backup automation and restore
rehearsal against a real protected backup, readiness gates, role separation,
ORM/index reconciliation and actual production adoption/deployment.

## Git diff / commit summary

All changes are confined to the requested SHG-11 worktree and SHG-14 scope.
SHG-12/13 foundations remain in place. The implementation is left as a reviewable
working-tree diff; no commit, push or deployment was performed.

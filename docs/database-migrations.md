# Database migrations (SHG-14)

Flyway is the only executable schema history. The Spring Boot 3.5.3 BOM manages
both `flyway-core` and `flyway-database-postgresql`; there is no separate Flyway CLI.
Migrations ship inside the same backend JAR and Docker image used for the web app.
**This is a runbook for future operator execution. Production adoption has not run.**

## Rules for changes

- Store migrations only in `backend/src/main/resources/db/migration/`.
- Use `V<integer>__<short_description>.sql`, for example `V3__add_example.sql`,
  then V4, etc. Allocate a new unused version; coordinate concurrent branches.
- Applied migrations are immutable, including formatting and comments. Flyway
  records checksums. Fix mistakes with a new reviewed migration, never by editing
  an applied file or repairing its checksum.
- Review locking, data preservation and compatibility with the running app before
  applying a migration. Test it on a fresh DB and an isolated restored copy.
- `baselineOnMigrate=false`, `cleanDisabled=true`, validation on migrate and strict
  naming are fixed in the command. Validation allows pending migrations, but rejects
  changed checksums and missing/future applied migrations. Routine operations expose neither clean nor
  repair, undo, arbitrary targets, alternate locations or arbitrary Flyway options.
- Do not use legacy SQL, Hibernate `create`/`update`, or Spring SQL initialization.

## Accepted V1 and V2

V1 is reconstructed from the SHG-11 production catalog captured on 2026-09-15:
PostgreSQL 16.11, database `savix`, schema `public`, no existing Flyway history.
It creates 10 tables, 9 serial/bigserial sequences, 86 columns, 46 constraints and
42 indexes. [Accepted metadata](database/accepted-v1-catalog.json) records columns,
constraints and indexes without application rows or operational credentials.
No history is inferred from the legacy filenames.

V1 retains `users.email` and `users.password_hash` as `varchar(100)`, physical
column order, existing defaults/nullability, timestamp-without-time-zone types,
foreign-key delete actions, partial uniqueness and the accepted object names.
In particular it preserves `transactions_amount_positive`,
`uq_categories_one_cycle_anchor_per_user`, and both
`idx_wallet_entries_wallet_history_order` and `idx_wallet_entries_wallet_ledger_order`.
It does not add the absent `idx_transactions_wallet_date`,
`idx_transactions_wallet_date_category` or `idx_categories_cycle_anchor` indexes.
It does not add uniqueness on `funds.fund_wallet_id` or change timestamp mappings
because of ORM annotations. Sequence start/increment/min/cache remain 1, no cycle,
with normal integer/bigint ranges. UUIDs use PostgreSQL's built-in `gen_random_uuid()`.
Roles, ownership, database creation, backups and sequence *values* are environment
concerns and are not copied from production into fresh databases.

V2 contains one ALTER TABLE statement widening only `users.email` and
`users.password_hash` to `varchar(255)`, preserving existing values. No other
schema cleanup is included. Both scripts use the explicitly selected default
schema, so isolated local schemas can also be migrated.

## Migration-only command

```sh
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar db info
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar db validate
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar db migrate
# First adoption only, after every prerequisite below:
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar db baseline --baseline-version=1 --confirm-baseline
```

`db` is dispatched before Spring starts. There is no application context, web
listener, scheduler, Redis connection, mail integration, JWT initialization or
business logic. Exit codes: 0 success, 2 invalid configuration/arguments, 1
connection/schema/Flyway failure. Library logs and raw exceptions are suppressed
because database errors can contain secrets; only bounded status and migration
information are printed. Inspect DB connectivity/permissions/history privately
when a command fails. A failed command blocks the release; do not try repair.

Required environment (no defaults, no `.env` auto-loading, no Spring profile or
`SPRING_FLYWAY_*` overrides):

| Variable | Meaning |
| --- | --- |
| `MIGRATION_ENV` | `local` (loopback host only) or `prod` (confirmed network target) |
| `DB_URL` | `jdbc:postgresql://HOST:PORT/DATABASE`; optional `?sslmode=...` only |
| `DB_USERNAME` | Explicit PostgreSQL login |
| `DB_PASSWORD` | Explicit nonblank password; never put it in a URL or command argument |
| `DB_SCHEMA` | Explicit existing application schema; e.g. `public` |
| `MIGRATION_EXPECTED_HOST` | Required in prod mode, must exactly match the URL host |

Prod mode also requires database `savix` and schema `public`. All modes require
PostgreSQL major version 16. URL credentials, missing ports, driver property
injection, placeholder values and extra arguments fail closed. Connection attempts
have a 10-second timeout. The command does not create schemas; a privileged
operator creates a non-public test schema separately if needed. Baseline additionally
checks all 10 expected tables, no history/extra tables, and both V1 user widths.
These guards **do not replace** full schema comparison and backup verification.

For a production-network invocation, put only these migration variables in an
operator-managed, mode-0600 file outside the checkout. Reuse the SHG-13 DB secret
source without printing it. Set `MIGRATION_ENV=prod`,
`MIGRATION_EXPECTED_HOST=savix-db`, `DB_URL=jdbc:postgresql://savix-db:5432/savix`,
`DB_SCHEMA=public` and the existing DB username/password. Export the path as
`MIGRATION_ENV_FILE`, and select the exact approved backend image as `BACKEND_IMAGE`.

```sh
# Same image and entrypoint as production; no Compose up or service dependencies.
docker run --rm --pull=never --network savix_savix_internal \
  --env-file "$MIGRATION_ENV_FILE" "$BACKEND_IMAGE" db info
```

Substitute `validate`, `migrate`, or the explicit baseline arguments for `info`.
Do not mount JWT keys or supply Redis/mail settings. The migration credentials
need the existing schema/table ownership privileges; login separation is deferred.
Never add baseline to a routine deployment. No release automation is implemented
in SHG-14: operators must gate web replacement on a successful migration.

## One-time existing production adoption (manual, NOT performed in SHG-14)

1. Select and verify the approved backend image and its immutable V1/V2 resources.
   Schedule a maintenance window, stop application writes/schedulers using the
   approved operational procedure, and keep them stopped through verification.
   Confirm the host, database `savix`, schema `public`, PostgreSQL 16.11 and role.
2. Check that `public.flyway_schema_history` is absent:
   `SELECT to_regclass('public.flyway_schema_history');` must return NULL.
   Re-capture the schema with the read-only query below and compare it against the
   accepted V1 catalog. Resolve any drift through review; do not auto-correct it.
   Also verify 10 ordinary tables, 9 owned sequences with the defaults above,
   no RLS, user triggers, views/materialized views or custom functions, and only
   the accepted `plpgsql` extension. The original SHG-11 inventory is authoritative;
   this metadata comparison is deliberately independent of Hibernate validation.
3. Take a new verified backup while writes remain paused. For the inventoried
   container/role, an operator can create a custom-format dump as follows, with
   `BACKUP_FILE` pointing to protected storage outside the checkout:
   ```sh
   umask 077
   docker exec savix-db pg_dump -U savix-admin -d savix --format=custom > "$BACKUP_FILE"
   test -s "$BACKUP_FILE"
   pg_restore --list "$BACKUP_FILE" > /dev/null
   ```
   Every command must succeed. Record its checksum, timestamp, target and release.
   Verify restoration into an isolated PostgreSQL 16 instance using
   `pg_restore --exit-on-error --no-owner --no-acl`, then compare schema and data
   counts/integrity. Listing a dump alone is not a verified restore. Preserve a
   protected off-host copy and the agreed recovery procedure before proceeding.
4. Run `db info` with the approved image and explicit production migration env.
   Expect V1 and V2 pending, no applied history. `info` is not a schema comparison.
5. Manually record **baseline version 1**:
   ```sh
   docker run --rm --pull=never --network savix_savix_internal \
     --env-file "$MIGRATION_ENV_FILE" "$BACKEND_IMAGE" \
     db baseline --baseline-version=1 --confirm-baseline
   ```
   This writes Flyway metadata only. **V1 must not execute against production.**
   Version 1 means the accepted pre-Flyway schema is already present. Version 0
   would make V1 pending and incorrectly replay CREATE TABLE against live tables.
6. Inspect the history with a read-only query:
   ```sql
   SELECT installed_rank, version, description, type, script, checksum,
          installed_by, installed_on, execution_time, success
   FROM public.flyway_schema_history ORDER BY installed_rank;
   ```
   Require exactly one successful row: version `1`, type `BASELINE`, description
   `Accepted production schema (SHG-14)`, checksum NULL. There must be no SQL row
   for V1 or V2 yet. Confirm both user widths are still 100 and app data unchanged.
   `db info` should identify V1 as baselined and V2 as pending.
7. Run `db validate`, then `db migrate` with the same image/env. Only V2 executes.
   Inspect history again: baseline row 1 followed by successful version `2`, type
   `SQL`, script `V2__widen_user_columns.sql`, with a checksum. Verify both widths
   are 255, preserved rows/constraints/indexes, then run `db validate` and `db info`.
8. Only after these checks follow the separately approved web rollout procedure.
   A second migrate must execute zero migrations. Do not baseline again. On any
   failure keep writes paused, inspect privately and follow the tested recovery
   plan; do not clean/repair or edit migration history.

Schema comparison from the repository root (operator-only production read):

```sh
docker exec -i savix-db psql -X -qAt -U savix-admin -d savix \
  -v ON_ERROR_STOP=1 -v schema=public < docs/database/inspect-catalog.sql > "$CATALOG_FILE"
python3 docs/database/compare-catalog.py "$CATALOG_FILE"
```

The comparison checks the recorded column/constraint/index metadata. It accepts
only two known equivalent PostgreSQL renderings of the `funds_status_check`
varchar-array-to-text-array cast; the allowed statuses remain identical. Use it
**before** baseline/V2; later history objects and widened columns intentionally
differ. The JSON contains schema metadata only, but store operational evidence
outside Git. Flyway `validate` checks migration history/checksums, not arbitrary
schema drift and not whether a manually baselined schema really matches V1.

## Fresh/local databases

Development Compose now creates only the PostgreSQL database and Redis. No SQL
file is mounted into `/docker-entrypoint-initdb.d`. Build once and explicitly
migrate before running the app (these values are development credentials only):

```sh
docker compose up -d
cd backend
./mvnw clean verify
export MIGRATION_ENV=local DB_URL=jdbc:postgresql://127.0.0.1:5432/savix
export DB_USERNAME=admin DB_PASSWORD=admin DB_SCHEMA=public
java -jar target/backend-0.0.1-SNAPSHOT.jar db info
java -jar target/backend-0.0.1-SNAPSHOT.jar db migrate
java -jar target/backend-0.0.1-SNAPSHOT.jar db validate
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

A fresh schema executes V1 and V2 with two successful SQL history rows and no
BASELINE row. A second migrate is a no-op. Never baseline an empty database.
Existing development volumes are not reset: an untracked non-empty schema fails
migration. Preserve anything needed and use a separately named fresh development
volume/database, or manually reconcile and verify V1 before explicit adoption.
Do not assume a database created by `01_init.sql` matches V1 (it does not).

Normal startup never migrates: Flyway auto-configuration is explicitly excluded
in the application class as well as disabled in YAML. Both default and production
configuration set Hibernate `ddl-auto: none` and SQL initialization `never`.
`validate` was not introduced for Hibernate because existing timestamp and fund
uniqueness assumptions need separate investigation. H2 tests retain `create-drop`
and explicitly disable Flyway. Production overrides must never enable ORM DDL.

## Legacy SQL and later work

The five legacy scripts remain at their original paths to preserve evidence and
external references: `01_init.sql`, `02_funds.sql`, `03_fund_currency_status.sql`,
`backend/src/main/resources/db/category_budgets.sql` and
`backend/src/main/resources/db/transaction_occurrence_link.sql`. They are historical
references, **not executable history**; their embedded old usage comments are
obsolete. Resource-root legacy SQL is excluded from the packaged JAR and only
`db/migration` is scanned. No automatic execution or legacy file moves occur.

SHG-15 adds the focused PostgreSQL 16.11 Testcontainers suite; see
[PostgreSQL migration integration tests](postgresql-migration-tests.md) for its
scenarios, architecture and `./mvnw verify -Ppostgres-it` command. SHG-16 implements
backup/migration gates, readiness and application rollout; SHG-17 adds CI and
immutable release builds. Broader schema/ORM reconciliation, index optimization,
role/privilege changes and actual production adoption remain separate work.
SHG-14 validation used only unit/regression tests and isolated PostgreSQL smoke
checks, with no production connection or deployment.

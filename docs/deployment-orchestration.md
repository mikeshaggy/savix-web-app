# Deployment orchestration (SHG-16)

SHG-16 adds a single-host release command on top of the fixed SHG-12 Compose,
SHG-13 runtime secrets, SHG-14 migration command and SHG-15 migration tests.
No production access, deployment, baseline, runner provisioning or GitHub Actions
configuration was performed while implementing this ticket.

## Deployment sequence

```text
host flock → read-only preflight → retain previous image pair
  → PostgreSQL custom dump → pg_restore --list → publish backup atomically
  → candidate backend: db validate → candidate backend: db migrate
  → backend-only Compose up → backend health + image identity
  → frontend-only Compose up → frontend /api/health + /login + image identity
  → final backend health → verify infrastructure identities/mounts → success
```

`scripts/deploy.sh` uses strict Bash and a Python standard-library helper.
The helper provides structured Compose inspection without sourcing a secret
file as executable shell code. It uses `fcntl.flock`, the same advisory host
lock as Linux `flock`, without an additional locking dependency. There is no
persistent deployment state machine or automatic application/database rollback.

`compose.production.yml` now probes backend readiness; its topology is unchanged.
`compose.deploy.yml` adds only the
profile-scoped `savix-migrate` service, which receives DB settings and runs
`db validate` or `db migrate` using the same pinned backend image as rollout.
It has no app ports, JWT mounts, Redis, SMTP or application startup.

## Required host inputs

Run on the Docker host with Bash, Python 3.9+, Docker access and Docker Compose
supporting JSON config, profiles, `--no-build`, `--no-deps` and `--pull never`.
The rehearsal's `!override` syntax additionally needs Compose 2.24.4+.
Use an explicit local Unix-socket Docker context, usually `default` on the Pi.
Remote contexts are refused so host paths, disk checks and locking remain local.

Before invoking the script, an operator must provision:

- An existing absolute runtime env file, mode 0600, following
  [the SHG-13 secret contract](production-secrets.md) and
  [production.env.example](production.env.example). Its values are authoritative;
  ambient application/Compose variables are not inherited.
- The existing matching JWT files, readable by backend UID/GID 10001:10001.
  Preflight checks file presence/readability/nonzero size; the SHG-13 application
  validates key format, curve and matching pair at startup. Do not generate
  replacement production keys.
- Local candidate backend and frontend images with immutable release tags or
  digests. Bare/floating tags such as `latest` are refused. Tag immutability is
  the release producer's contract; the script resolves tags to image IDs once
  and supplies those exact IDs to Compose, protecting against later retagging.
- Existing external networks, the exact PostgreSQL and Redis volumes, and running
  DB, Redis and both exporters matching the SHG-12 model. Current app containers
  and their local images must exist for forward deployment. Recovery accepts
  unhealthy, stopped or missing app containers using the original retained pair.
- Existing absolute backup and state directories, writable by the deploy user
  and not writable by group/others. Use mode 0700 for state and protected backup
  storage. A persistent absolute lock-file path with an existing private parent.
  All manual, backup and future automated invocations **must use the same path**.

The existing nightly backup directory is
`/home/mikeshaggy/homelab/savix/backups/postgres`; select it explicitly with
`--backup-dir` if appropriate. No nightly script, cron entry or retention policy
is changed. SHG-16 performs no backup deletion; existing nightly retention can
still apply when using its directory. Store records outside disposable checkouts
so the original failed attempt remains available after retries.

The pre-existing production schema still requires the separate, reviewed,
manual [SHG-14 baseline adoption](database-migrations.md#one-time-existing-production-adoption-manual-not-performed-in-shg-14).
Routine deployment never performs baseline, repair or clean.

## Preflight and deployment commands

These are operator examples, not commands executed during SHG-16. Set these path
variables to existing protected host locations; none contains a password.
Run from the checkout containing the scripts and Compose files.

```bash
RUNTIME_ENV=/absolute/protected/runtime.env
BACKUP_DIR=/home/mikeshaggy/homelab/savix/backups/postgres
STATE_DIR=/absolute/protected/deployment-records
LOCK_FILE=/absolute/protected/savix-deploy.lock
BACKEND_IMAGE=savix-backend:git-REPLACE_WITH_COMMIT
FRONTEND_IMAGE=savix-frontend:git-REPLACE_WITH_COMMIT

common=(--docker-context default --env-file "$RUNTIME_ENV"
        --backup-dir "$BACKUP_DIR" --state-dir "$STATE_DIR"
        --lock-file "$LOCK_FILE")
candidate=(--backend-image "$BACKEND_IMAGE" --frontend-image "$FRONTEND_IMAGE")

./scripts/deploy.sh "${common[@]}" "${candidate[@]}" --preflight
./scripts/deploy.sh "${common[@]}" "${candidate[@]}"
```

Read-only preflight creates no files or containers and takes no lock. Every
mutating invocation acquires the nonblocking host lock first and reruns preflight.
Do not unlink the lock file: replacing its inode defeats mutual exclusion. A
second deployment, standalone backup or rollback fails immediately on contention.
Direct Docker commands and the existing nightly job do not acquire this lock.

Preflight checks Docker/Compose access, candidate/previous local image presence,
quiet Compose interpolation, required nonblank settings, JWT files, all external
networks/volumes, infrastructure mounts and running state, matching database
credentials, database/network configuration, authenticated DB reachability and
PostgreSQL 16 server/client versions. It checks free space on host backup/state
filesystems and the Docker/database filesystems. Default minimum is 1024 MiB;
backup storage must also have at least twice `pg_database_size`. This is an
estimate, not a reservation or a guarantee against concurrent disk consumption.
Preflight cannot guarantee app startup or future database availability.

`--project` defaults to `savix`. `--compose-override` is provided for isolated
rehearsals only; do not supply one for production. No images are built or pulled
by either production script. Candidate images must already be loaded by SHG-17.

## Backup gate

`backup-db.sh` provides the same gate independently:

```bash
./scripts/backup-db.sh "${common[@]}" "${candidate[@]}"
```

It intentionally shares all inputs, preflight and the deployment lock. It stops
after backup and does not run Flyway or replace containers.

Backup uses the running PostgreSQL 16 container's `pg_dump --format=custom` and
`pg_restore --list`. TCP authentication uses the existing container's password
without including it in command arguments, URLs or console output. The file is
streamed to a unique UTC timestamp/attempt filename ending `.dump.partial`,
flushed to disk, checked for nonzero size and streamed through `pg_restore
--list`. A private metadata sidecar records timestamp, database, PostgreSQL major,
size, SHA-256 and candidate image references/IDs. Only then is the dump atomically
renamed on the same filesystem to `.dump`. Files use mode 0600.

Any dump, inspect, metadata or rename failure blocks Flyway. Partial files remain
for investigation; the script deletes no backups. Default backup/inspection
command timeout is 600 seconds each (`--backup-timeout`). There is no full restore
test or off-host copy in this gate. The dump is PostgreSQL's transactionally
consistent snapshot while the current app remains live; writes after that snapshot
are not in it. Restore readiness and business write pauses remain adoption concerns.

## Migration and rollout gates

After backup, the script executes `db validate`, then `db migrate` as separate
one-off Compose runs with `--no-deps --pull never`. Each uses the candidate
backend image ID and exactly the application DB credentials/target. Default
timeout is 300 seconds per command (`--migration-timeout`). The existing command
rejects untracked populated schemas, wrong targets and non-16 databases.

A nonzero exit or timeout prevents all application replacement. A timed-out or
interrupted one-off is explicitly removed by its unique attempt name before the
lock is released when Docker remains reachable. If Docker is unavailable, inspect
for a `savix-migrate-<attempt>-<operation>` container before retrying. Already
committed migrations are not undone; old and new application versions must be
compatible with the forward schema changes. The old application stays running
during migration, so migrations must also tolerate its concurrent traffic.

Each rollout uses only:

```text
compose up -d --no-deps --no-build --pull never savix-backend
compose up -d --no-deps --no-build --pull never savix-frontend
```

Only the specified application service can be replaced. DB, Redis and exporters
are never passed to `up`. Their container IDs and mounts are compared again after
a healthy rollout. Compose replaces a single app instance, so short interruption
is expected; this is not a zero-downtime strategy.

## Health verification

The scripts probe inside the actual candidate containers, avoiding a false pass
from an unrelated host port. Backend uses the existing `wget` tool to request
`/actuator/health/readiness`, requiring HTTP success and JSON `status: UP`.
Frontend uses Node `fetch`: `/api/health` must return HTTP 200 and `status: UP`, then `/login`
must return HTTP 200 without redirect. The established frontend route contacts
backend `/actuator/health/readiness`, exercising the frontend-to-backend path.
The proxy base must point to the Compose backend. A Node process or login page alone cannot
pass the gate. This does not test external DNS, Cloudflare Tunnel or user sign-in.

Checks verify running state, actual Docker image ID and configured pinned image
reference before and after requests. Backend is checked again after frontend.
Defaults: 30 attempts, 5 seconds between attempts; each HTTP request times out
at 5 seconds, with an outer Docker-exec deadline of 10 seconds for backend or
15 seconds for frontend. Tune with `--health-attempts` and `--health-interval`.
The dedicated Spring Boot readiness group includes only `readinessState`, `db`,
`redis` and `diskSpace`. SMTP remains enabled in aggregate `/actuator/health` for
monitoring, but is excluded from deployment readiness. With DB/Redis/app healthy,
a mail outage can make aggregate health return 503 while readiness and frontend
`/api/health` return 200. A DB or Redis outage makes both readiness paths fail.
The Compose backend health check uses this same readiness endpoint; monitoring
can continue using aggregate health to observe mail degradation independently.

Failures capture the last 80 backend/frontend log lines from the last 10 minutes,
retaining at most 32 KiB per service in private attempt files. Known configured
secrets, PEM blocks and common credential assignments are redacted. Do not treat
redacted diagnostics as public artifacts: application logs may contain other
sensitive business data. Raw command errors, full Compose config, container env
and SQL exceptions are never printed or persisted by these scripts.

## Failure behavior

| Failure | Result |
| --- | --- |
| Missing build/image or invalid preflight | Stop; no backup, migration or application replacement |
| Backup creation/inspection failure | Stop before Flyway; existing applications unchanged; partial retained |
| Flyway validate/migrate failure | Stop immediately; both existing app container identities unchanged; no repair/clean |
| Backend rollout/health failure | Stop before frontend; failed attempt retains previous image pair and bounded logs |
| Frontend rollout/health failure | Fail with backend/frontend diagnostics and retained previous pair |
| Lock contention | Immediate failure before preflight/mutation |
| Cleanup failure | No post-deployment cleanup exists, and no failure invokes automatic rollback |

Scripts return zero only for completed preflight, backup-only or healthy rollout;
failures return nonzero. Each mutating attempt creates a unique `release.json`
with prior/candidate image IDs and references, host/project/container identities.
It is written before backup/migration/rollout and never overwritten by a retry.
`failure.json` identifies the failed stage; `success.json` records healthy rollout.
There is no mutable "previous" pointer that can lose the last usable pair.

## Manual application rollback

Choose the `release.json` printed by the failed attempt, inspect its `previous`
pair, and verify those images support the current forward-migrated schema. Run:

```bash
FAILED_RELEASE=/absolute/protected/deployment-records/ATTEMPT/release.json
./scripts/deploy.sh "${common[@]}" --rollback "$FAILED_RELEASE"
```

Do not pass candidate image options in rollback mode. Rollback verifies that the
record matches the current Docker daemon, project and container names and that
both recorded image IDs still exist. It runs the same preflight/lock, creates a
new record of the pair it is replacing, restores backend first, verifies health,
then restores frontend and verifies end-to-end health. It skips backups and
Flyway entirely. A failed rollback stops and retains its own prior-pair record. If a container
was missing before recovery, that entry in the new record is null; use the original
complete release record for subsequent recovery. Never roll back database schema or reverse migrations through this script.

Keep the current and previous local images and all failed-attempt records. No
automatic image/volume cleanup is provided. Older releases may require a forward
fix if incompatible with the migrated schema; restoring a DB is a separate reviewed
incident procedure and may lose writes since the snapshot.

## Troubleshooting and prohibited operations

- Preflight failure: check file modes/paths, local image existence, available space,
  correct local Docker context, external resource identities, existing container
  state and secret consistency. Do not print interpolated Compose config or env.
- Backup failure: inspect storage capacity/permissions and the DB role's dump
  privileges; keep partials for investigation. Listing a dump is not a restore test.
- Flyway failure: privately inspect history/schema and the migration command exit
  condition. Do not repair checksums, baseline automatically, clean or retry
  blindly after interruption. See the SHG-14 adoption runbook.
- Health failure: use the protected bounded logs, inspect dependency health and
  JWT readability/validity. Frontend cannot advance after a failed backend gate.
  Confirm the retained release before a manual rollback.
- Interrupted process/host failure: lock releases with the process; records remain.
  Inspect current images, DB history and any attempt-named migration container
  before restarting. A host crash/SIGKILL cannot run cleanup handlers.

Never use `docker system prune -a`, `docker volume prune`, `docker compose down -v`,
`--remove-orphans`, or the development Compose file on production. Do not run a
whole-project `compose up` for a release. Do not remove database/Redis volumes,
regenerate JWT keys, or execute Flyway repair/clean to force a deployment through.

## Local validation and SHG-17 boundary

See [the SHG-16 validation report](shg-16-validation.md). The reproducible Docker
rehearsal is `python3 scripts/tests/rehearse-deployment.py`. It is intentionally
local-Docker-Desktop-only, uses disposable resources and dummy secrets, and builds
fixture images outside the deploy scripts. It requires the locally available
SHG-13 backend runtime, a packaged current backend JAR, PostgreSQL
`16.11-bookworm` and Redis `7-alpine`. The frontend is rebuilt from the current
Dockerfile, including its normal lint/build gates. The rehearsal leaves SMTP
health enabled with an unavailable dummy mail endpoint, verifies aggregate mail
failure independently of readiness, and stops/restarts only its disposable DB
and Redis to verify direct and frontend-proxied readiness failures/recovery.
Component details are enabled only inside this isolated fixture for assertions;
production details remain authorization-controlled. No production files or
resources are read.

SHG-17 will build/test and deliver immutable application images, configure GitHub
Actions and the production runner, set workflow concurrency, and invoke these
scripts with explicit inputs and the shared host lock path. Runner installation,
secret provisioning, host setup, automatic pruning and actual deployment are
not included. Adoption/baseline approval, full restore rehearsal and off-host
backup policy remain separate operational work.

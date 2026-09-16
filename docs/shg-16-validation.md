# SHG-16 validation and completion report

Completed on 2026-09-16 in `/Users/shaggy/dev/active/savix-shg-11`, based on
`bd726b2` (SHG-15), branch `shg-11-ci-cd-flyway`. SHG-12 through SHG-15 Dockerfile, topology and migration foundations are retained.
The targeted readiness follow-up adds Actuator configuration and updates the
backend Compose probe and frontend health route.
No production access, mutation, deployment, GitHub Actions configuration or
production runner setup occurred.

## SHG-16 implementation summary

Added a single-host deployment entry point with read-only preflight, host flock,
verified PostgreSQL backup, candidate-image migration gates, backend-first rollout,
end-to-end frontend health checks and explicit application-image recovery. The
strict Bash entry points delegate JSON/process/file handling to Python's standard
library. No third-party Python packages or general deployment framework are used.

## Files changed

| File | Change |
| --- | --- |
| `scripts/deploy.sh` | Strict-shell deployment/preflight/manual rollback entry point |
| `scripts/backup-db.sh` | Standalone backup entry point sharing configuration and lock |
| `scripts/deployment.py` | Preflight, locking, backup, migration, rollout, diagnostics and recovery |
| `backend/src/main/resources/application.yml` | Dedicated readiness group excluding SMTP |
| `compose.production.yml` | Backend readiness health check; topology unchanged |
| `frontend/src/app/api/health/route.js` | Proxy backend readiness instead of aggregate health |
| `compose.deploy.yml` | Profile-scoped migration-only service using the candidate backend |
| `scripts/tests/rehearse-deployment.py` | Reproducible isolated Docker integration rehearsal |
| `docs/deployment-orchestration.md` | Operator deployment, failure and rollback runbook |
| `docs/shg-16-validation.md` | This report |
| `README.md` | Deployment/validation links |
| `docs/production-docker.md` | SHG-16 integration link without changing SHG-12 topology |

## Deployment flow

Acquire shared nonblocking host lock; validate configuration, local images and
existing infrastructure; retain the previous image pair; back up PostgreSQL;
validate and migrate with the candidate backend; replace only backend and verify
health; replace only frontend and verify backend-dependent health and login;
recheck backend and infrastructure identities. Image inputs are resolved to local
immutable IDs before Compose runs. No build, pull or whole-project rollout occurs.

## Backup behavior

PostgreSQL 16 `pg_dump --format=custom` streams to a private timestamped partial
file. `pg_restore --list` must succeed before atomic publication as `.dump`.
Metadata retains byte count, SHA-256, timestamp, database and candidate images.
Incomplete files remain for investigation. Deployment never deletes backups or
changes the existing nightly mechanism. No full restore rehearsal is claimed.

## Migration gate

Separate candidate-image `db validate` and `db migrate` one-off services run only
after successful backup. Nonzero exits and timeouts prevent application replacement.
No baseline, repair, clean or reverse migration is invoked. Both checksum validation
and actual SQL migration failures were exercised against disposable PostgreSQL.

## Health verification

Backend `/actuator/health/readiness` requires success and JSON `status: UP`. Frontend
`/api/health` requires HTTP 200 and `status: UP`; `/login` requires HTTP 200.
Probes run inside the selected containers with bounded HTTP/Docker timeouts and
retries. The real Next route reaches backend readiness, including its dependency-failure responses.
Running state, actual image ID and configured pinned image reference are checked.
Failure captures bounded, redacted backend/frontend logs in private attempt records.

The readiness group contains exactly `readinessState`, `db`, `redis` and
`diskSpace`. Mail health remains enabled with an unavailable dummy SMTP endpoint:
aggregate health returns 503 with mail DOWN, while direct readiness and the frontend
proxy return 200. Stopping either the disposable DB or Redis makes both readiness
paths return 503; restarting the same container restores both to 200. Component
details are exposed only in the isolated fixture for assertions. Production details
remain authorization-controlled. DB and Redis health use actual disposable services. Exporters are inert fixture
containers: their identity/mount preservation is tested, not exporter functionality.
External DNS, Cloudflare Tunnel and authenticated user sessions are outside this gate.

## Rollback model

Every attempt retains an immutable `release.json` before migration/rollout with
previous and candidate references/IDs. Retries never overwrite earlier records.
`deploy.sh --rollback /absolute/path/release.json` restores that record's previous
backend/frontend IDs under the same lock and health gates, without backups or
Flyway. It also recovers when a failed rollout leaves the backend container missing.
The new recovery record marks any absent prior container/image as null; use the
original complete release record for subsequent recovery. Images must remain local
and schema-compatible; database rollback is never automatic.

## Failure scenarios tested

All passed in the final isolated rehearsal:

| Scenario | Evidence/result |
| --- | --- |
| Successful deployment | Real backend JAR and Next server change from A/A to B/B; all health endpoints and pinned IDs pass |
| Missing candidate image | Failure before any dump or container replacement |
| Backup failure | Injected `pg_dump` exit 42 leaves only a partial file; Flyway never starts; all container identities remain unchanged |
| Flyway validation failure | Actual V2 checksum mismatch blocks rollout; checksum remains unrepaired |
| Flyway migration failure | Pending V2 fails against a deliberately removed column; both old application container IDs remain unchanged |
| Backend health failure | Candidate migration succeeds but web health fails; frontend ID is unchanged; redacted bounded logs and prior pair persist |
| Frontend health failure | Fixture Node server can respond but `/api/health` returns 503; deployment fails with diagnostics |
| SMTP unavailable | Deployment succeeds; readiness and frontend proxy are UP while aggregate health reports mail DOWN |
| DB unavailable | Backend readiness and frontend proxy return 503 with DB DOWN; both recover after restart |
| Redis unavailable | Backend readiness and frontend proxy return 503 with Redis DOWN; both recover after restart |
| Lock contention | Independent host flock prevents the script from entering deployment |
| Manual rollback after backend failure | Restores previous B/B pair and health |
| Manual rollback after frontend failure | Restores B/B; database migration history and backup count remain unchanged |
| Original release rollback | Original A/A image IDs restored with healthy endpoints |
| Infrastructure preservation | PostgreSQL, Redis and both exporter container IDs and all infrastructure mounts remain unchanged through the required scenarios |
| Missing-container recovery | Delete only the disposable backend; manual rollback recreates it from the original retained image ID |
| Read-only preflight | No files, containers or mounts changed |
| Insufficient disk | Unrealistic configured free-space minimum stops preflight before mutation |
| Standalone backup | Backup completes with no application replacement |

Successful backups were checked for matching SHA-256 sidecars and private file
permissions. The deployment's `pg_restore --list` gate ran on every completed dump.
No post-deployment cleanup path exists, so cleanup cannot initiate a rollback.
Test-harness resource removal is separate from production deployment behavior.

## Validation performed

- Final `python3 scripts/tests/rehearse-deployment.py` exited 0 with 19 grouped PASS
  assertions covering the scenarios above. Evidence is retained locally under
  `.local/shg-16/shg16-c11814b9/` (ignored by Git), including per-attempt console
  results, frontend build log, protected release records, redacted failure logs and
  disposable dumps.
- Actual local Docker Desktop Linux/ARM64 daemon; Docker 29.4.3 and Compose 5.1.3.
  PostgreSQL `16.11-bookworm`, Redis `7-alpine`, freshly packaged backend JAR
  over the SHG-13 runtime image, and a current frontend image built with the
  existing Dockerfile (Node 22).
  Unique rehearsal labels provide distinct A/B application image IDs.
- Dummy env settings and generated disposable JWT keys only. Test resources use
  random `shg16-…` names, separate external networks/volumes and no published ports.
  All rehearsal containers, networks, volumes and tagged fixture images were removed.
- Backend `./mvnw -B -ntp verify -Ppostgres-it`: 743 existing tests and five
  PostgreSQL integration tests passed, zero failures/errors/skips; BUILD SUCCESS.
  Log: `.local/shg-16/readiness-backend-validation.log`.
- Frontend Dockerfile `npm run lint && npm run build`: passed, 31 pages generated.
  The pre-existing `src/utils/helpers.js:79` anonymous-default-export warning remains.
- Bash syntax checks, Python AST parsing, changed-file review and `git diff --check`.
  No production secrets, private-key contents or database passwords appear in tracked
  changes. Application changes are limited to health configuration and the frontend
  readiness route. Existing migrations are unchanged; the local rehearsal adds readiness assertions.

This verifies local orchestration, not production adoption, Raspberry Pi load,
full restoration, SMTP availability or external routing. The actual production
baseline and protected key/env provisioning remain operator prerequisites.

## Items deferred to SHG-17

GitHub Actions, production runner installation/configuration, workflow concurrency,
CI image builds/tests and immutable release delivery, secure workflow inputs and
invocation of these host scripts. Automatic pruning is deferred. Production
baseline/adoption, full restore rehearsal and off-host recovery policy remain
separate operational work; none was performed here.

## Git diff / commit summary

Twelve SHG-16 files added/updated (seven new files and five existing files updated).
The change is left uncommitted for review on the existing SHG-11 branch; no commit,
push or PR was created. The only runtime foundation changes are the requested
readiness configuration, backend Compose probe and frontend health route.

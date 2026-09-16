# SHG-17 validation and completion report

Implemented on 2026-09-16 in `/Users/shaggy/dev/active/savix-shg-11`, on the existing
`shg-11-ci-cd-flyway` branch, based on SHG-16 commit
`69c914b81e13efd2a397b065cc6922f22e26cf08`.

## SHG-17 implementation summary

Added a guarded production GitHub Actions workflow, a CI-only containerized
validation/image helper, regression coverage and an operator provisioning guide.
SHG-12 through SHG-16 application, Dockerfile, Compose, migration and deployment
implementations are unchanged. Deployment orchestration remains entirely SHG-16.

**Implementation complete. Production runner installation/registration/startup,
production baseline execution and first production deployment remain unperformed.**
No production connection, host modification or GitHub configuration change was
made. The enable variable is deliberately required before any deployment job runs.

## Files changed

| File | Result |
| --- | --- |
| `.github/workflows/deploy.yml` | Push-main workflow, guards, concurrency, validation/build gates, SHG-16 calls and safe summary |
| `.github/actionlint.yaml` | Declares the custom production runner label for workflow validation |
| `scripts/ci/production-build.sh` | Local resource checks, containerized validation, immutable ARM64 image publication/reuse |
| `scripts/tests/test-production-workflow.py` | Twelve isolated shell/workflow regression tests; fake Docker and fake deployment interface |
| `docs/github-actions-runner.md` | Exact provisioning/registration/service steps, permissions, host paths, trust model, adoption and troubleshooting |
| `docs/shg-17-validation.md` | This completion and validation record |
| `README.md` | SHG-17 documentation links |

## GitHub Actions workflow

Only pushes to `main` in the intended repository are eligible. The repository
variable `SAVIX_PRODUCTION_ENABLED=true` is an explicit adoption gate, and the job
uses environment `production`. No PR or manual-dispatch trigger exists.
Concurrency is `savix-production`, with `cancel-in-progress: false`; GitHub's
pending-run replacement semantics are documented. All expensive stages run
sequentially. Job/step timeouts and an initial 8 GiB free-space gate limit exposure
to stuck or oversized builds without introducing a scheduler.

## Runner model

A dedicated `savix-runner` service account, repository-scoped ARM64 runner,
`self-hosted`/`linux`/`arm64`/`savix-production` labels and systemd service are
specified in the [runbook](github-actions-runner.md). The work directory is
`/var/lib/savix-runner/work`; a pre-checkout guard rejects other work roots. The
live production checkout is never an Actions workspace. Java and Node are supplied
by the existing pinned container toolchains, not host installations.

## Image build/tag strategy

Both application images use full event-SHA tags and `VCS_REF` revision metadata,
built locally for `linux/arm64` and loaded into the local daemon without a registry.
The frontend runtime build reuses its verified build-stage cache. The fixed backend
Dockerfile retains its own ordinary verification in addition to the earlier full
PostgreSQL suite. Existing valid SHA tags are reused; mismatches fail, and a new
candidate is checked before its immutable tag is published. No floating release
tags or overwriting existing tags with new build content are used.

## Deployment invocation

The workflow directly calls `./scripts/deploy.sh --preflight`, followed by
`./scripts/deploy.sh`, with identical explicit image, env, backup, state, lock and
Docker-context arguments. SHG-16's mutating call retains its host lock and fresh
preflight. Exit codes propagate; no retry, migration, rollout, rollback, repair,
clean or database restore implementation was added to Actions.

## Security model

The token has only `contents: read`; the checkout action is commit-pinned and sets
`persist-credentials: false`. Only non-secret host paths/context names go into
repository variables. Runtime env and existing JWT keys remain protected host
files; no Actions secrets or remote transfer are required. Summaries contain only
SHA, image refs and outcomes; SHG-16's own step output identifies private records.
No runtime files, diagnostics, application logs or backups are uploaded.

Docker access is host-level production control. Labels/environment settings do
not isolate this runner from another authorized repository workflow. Excluding
arbitrary PR/fork code across the repository is a required adoption policy check,
not a security guarantee supplied by this one YAML file.

## Validation performed

| Check | Result |
| --- | --- |
| actionlint 1.7.12 with custom-label configuration | Passed workflow syntax, expressions, schema and embedded shell checks |
| ShellCheck 0.11.0 and `bash -n` | Passed helper and workflow shell checks |
| Twelve isolated workflow/helper regression tests | Passed |
| Backend `./mvnw -B -ntp verify -Ppostgres-it` in isolated ARM64 Linux Docker | 743 ordinary tests + 5 PostgreSQL integration tests passed; zero failures/errors/skips |
| Frontend Dockerfile `build` target | Passed; existing matching npm install/lint/build layers were reused from cache |
| Both real ARM64 runtime image builds | Passed local existence, architecture and revision inspection |
| Actual helper image publication and repeated invocation | Both SHA tags published; reruns reused them with unchanged image IDs |
| SHG-16 compatibility | Real `deploy.sh --help` plus exact quoted argument assertions; preflight/deploy execution stubbed |
| Secret-pattern review and foundation diff | No credentials/key material added; fixed implementation files unchanged |
| Production operations | None performed |

Regression tests exercise validation and image-build failures before deployment,
preflight failure before the mutating call, deployment failure without retry,
workspace isolation, explicit quoted paths containing spaces and literal dollar
signs, permissions/triggers/labels/concurrency, revision/architecture mismatch,
immutable reuse, a tag appearing during build, remote-Docker refusal and safe
summary generation. They run actual YAML shell bodies with command fakes, not
production scripts against a host environment.

Local Docker Desktop did not provide native host-loopback connectivity to Ryuk
for the initial containerized backend run. Verification therefore used a disposable
nested ARM64 Linux Docker daemon, with the Java container sharing its network
namespace and actual socket group. The full required Maven command then passed
with Ryuk enabled and the unchanged PostgreSQL tests. The nested daemon, validation
container and socket volume were removed. This emulates Linux networking; it does
not claim the real Raspberry Pi runner or GitHub service was tested.

Actual image-helper checks used the local `default` socket only after confirming
it resolves to the same Docker Desktop daemon. Local image refs carry current HEAD
`69c914b...`; the uncommitted SHG-17 changes do not alter application build contexts.
Actions will use the SHA of the eventual pushed commit. Local images/build caches
and ignored validation logs under `.local/shg-17` were retained; nothing was pushed.

Reproduce the static/regression checks with Python + PyYAML 6.0.3, actionlint
1.7.12 and ShellCheck 0.11.0 available:

```bash
python3 scripts/tests/test-production-workflow.py
actionlint .github/workflows/deploy.yml
shellcheck scripts/ci/production-build.sh
bash -n scripts/ci/production-build.sh
git diff --check
```

## Production adoption steps still pending

1. Repository/PR trust-policy review, main protection and production environment rules.
2. Protected host paths/account permissions, shared lock agreement and backup/recovery readiness.
3. Separately approved ARM64 runner installation, registration, service start and reboot verification.
4. Separately approved SHG-14 existing-production baseline adoption.
5. Configuration/enablement and the separately approved first production deployment.

The detailed [first-adoption checklist](github-actions-runner.md#security-model-and-first-production-adoption-checklist)
keeps these operational actions separate from implementation completion.

## Follow-up risks / tickets

Operational adoption remains separate work. Confirm repository policy can exclude
untrusted runner execution; if it cannot, do not register this production runner.
Measure Pi resource headroom/timeouts, agree controlled image/cache retention,
confirm full restore/off-host backup readiness and coordinate nightly backups.
Keep runner, pinned action and dependency updates under review. No new external
tickets were created and none of these concerns triggered a production change.

## Git diff / commit summary

Six new files and one README update on the existing SHG-11 branch. SHG-12 through
SHG-16 implementation sources are unchanged. Changes remain uncommitted for review;
no commit, push, pull request or merge was performed.

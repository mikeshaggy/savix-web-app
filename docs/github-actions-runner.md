# GitHub Actions deployment and ARM64 runner (SHG-17)

**Implementation complete; operational adoption has not been performed.** No
production runner was installed, registered, started or modified. No production
baseline or deployment was executed. All commands below are a future operator
runbook, not a record of executed commands. SHG-12 through SHG-16 remain fixed.

## Workflow contract

[deploy.yml](../.github/workflows/deploy.yml) runs only for pushes to `main` in
`mikeshaggy/savix-web-app`, and only when the **repository variable**
`SAVIX_PRODUCTION_ENABLED` is exactly `true`. Leave it unset until adoption is
approved and complete. The job targets environment `production` and all four
runner labels: `self-hosted`, `linux`, `arm64`, `savix-production`.

```text
push main → enable/repository/ref guards → dedicated runner workspace check
  → credential-free checkout of event SHA → local Docker/8 GiB disk gate
  → full backend verify + PostgreSQL integration tests
  → frontend npm ci + lint + build in the Dockerfile build stage
  → backend immutable image → frontend immutable image
  → SHG-16 read-only preflight → SHG-16 locked deployment → safe job summary
```

There is no pull-request trigger, `pull_request_target`, arbitrary checkout ref,
workflow dispatch, registry push, migration command, Compose rollout, deployment
retry or recovery implementation in this workflow. GitHub's **Re-run jobs** is
still a manual deployment attempt: inspect the original SHG-16 records and current
schema/images first, especially after cancellation or partial deployment. Prefer
a reviewed forward-fix push to `main` where appropriate.

Workflow concurrency is `savix-production`, with `cancel-in-progress: false`.
A new push does not interrupt the running release. GitHub retains at most one
pending run in this group; newer pending runs replace older pending runs, and
ordering is not guaranteed. This is not a FIFO queue that deploys every commit.
See [GitHub concurrency semantics](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency).
The SHG-16 nonblocking host lock independently protects deployment, backup and
manual recovery. Use its **same existing lock path/inode** everywhere; never
unlink, rotate or recreate a live lock. The existing nightly backup job and direct
Docker operations do not acquire this lock; coordinate them operationally as in
[SHG-16](deployment-orchestration.md).

Steps run sequentially with explicit timeouts; the overall job is capped at
120 minutes. The cap can terminate a deployment when earlier steps consumed most
of the budget. Treat any timeout/interruption as an incident to inspect, not a
reason for an automatic rerun. The disk gate requires 8 GiB free on the workspace
and Docker storage filesystems before expensive work. SHG-16 performs its own
fresh disk, backup capacity and infrastructure checks immediately before mutation.
Space checks reserve nothing. There is no resource scheduler or automatic pruning.

## Runner account and filesystem

Provision **one** dedicated, repository-scoped, 64-bit Linux runner on the Pi.
Use a supported Debian/Ubuntu ARM64 system with systemd and an already functioning
local Docker daemon. Do not reinstall Docker, migrate its data root or change
production networks/volumes as part of runner setup.

| Purpose | Proposed location / rule |
| --- | --- |
| Account and home | `savix-runner`, `/var/lib/savix-runner`, no login password or sudo grant |
| Runner application | `/opt/savix-actions-runner`, owned by `savix-runner` for runner updates |
| Runner work directory | `/var/lib/savix-runner/work`, explicitly passed to registration |
| Managed checkout | GitHub creates it below the work directory, typically `savix-web-app/savix-web-app` |
| Runtime env | `/etc/savix/runtime.env`, runner-owned, mode 0600, outside work directory |
| Existing JWT pair | Existing protected absolute files, read-only to runner and backend UID/GID 10001:10001 |
| Backups | Approved existing backup directory or `/var/lib/savix-deploy/backups`, runner-owned mode 0700 |
| Attempt records | `/var/lib/savix-deploy/records`, runner-owned mode 0700 |
| Shared deployment lock | The existing agreed absolute SHG-16 path; example for a previously unprovisioned host: `/var/lib/savix-deploy/savix-deploy.lock` |
| Docker context/socket | Runner user's `default` context pointing to `unix:///var/run/docker.sock` |

The live checkout `/home/mikeshaggy/homelab/savix/savix-web-app` contains untracked
deployment assets. **Never use it, or another production checkout, as the runner
work directory or checkout path.** Do not symlink the runner work root to another
location. The workflow checks this before checkout can clean files. Keep runtime
env, keys, backups and state outside the entire runner work root, including its
temporary directories. Do not put secrets in runner `.env`, `.profile`, systemd
`Environment=`, build contexts or workflow-level environment values.

Future provisioning commands, executed by an administrator after approval:

```bash
# Confirm aarch64/arm64 and a supported 64-bit OS first.
uname -m
getconf LONG_BIT
# Install only missing client/host dependencies; preserve the existing Docker daemon.
sudo apt-get update
sudo apt-get install --yes ca-certificates curl git python3 acl
# Docker CLI, Compose and Buildx must already be installed from the host's
# approved Docker package source; verify them below before continuing.
sudo useradd --create-home --home-dir /var/lib/savix-runner \
  --shell /bin/bash --user-group savix-runner
sudo passwd --lock savix-runner
sudo chmod 0700 /var/lib/savix-runner
sudo usermod --append --groups docker savix-runner
sudo install -d -m 0750 -o savix-runner -g savix-runner /opt/savix-actions-runner
sudo install -d -m 0700 -o savix-runner -g savix-runner /var/lib/savix-runner/work
sudo install -d -m 0750 -o root -g savix-runner /etc/savix
sudo install -d -m 0700 -o savix-runner -g savix-runner \
  /var/lib/savix-deploy /var/lib/savix-deploy/backups /var/lib/savix-deploy/records
```

These commands assume the account/directories do not already exist. Inspect and
adapt existing ownership deliberately; do not recursively chown production trees.
No `sudo` command runs inside Actions. Administrative sudo is only for provisioning
and service management. Docker group membership supplies noninteractive daemon
access and must take effect in a fresh runner login/service process.

## Protected runtime inputs

Reconcile locations with the accepted production inventory before copying or
changing anything. Follow the fixed [SHG-13 secret contract](production-secrets.md)
and [SHG-16 host inputs](deployment-orchestration.md#required-host-inputs).

1. An administrator copies the approved runtime env into `/etc/savix/runtime.env`
   using `install -m 0600 -o savix-runner -g savix-runner`. Supply the protected
   source path privately; never echo/cat the file or embed its contents in a shell
   command. Preserve current DB, mail, proxy and JWT settings. The image arguments
   supplied by SHG-16 override the env file's application image values.
2. Preserve the existing JWT bytes and pair. Do not generate replacement keys.
   Keep their current paths if practical. Grant `savix-runner` read access to
   those two files, and traversal on each necessary private parent directory,
   with narrowly scoped ACLs; preserve backend UID/GID 10001:10001 readability.
   For example, after setting the approved path variables in an operator shell:

   ```bash
   sudo setfacl -m u:savix-runner:r-- "$EXISTING_JWT_PRIVATE_FILE" "$EXISTING_JWT_PUBLIC_FILE"
   # Repeat only for private ancestors that otherwise deny traversal:
   sudo setfacl -m u:savix-runner:--x "$EXISTING_JWT_PARENT"
   ```

   Keep JWT paths absolute in the protected runtime env. Never mount the host
   secret directories into test/build containers. ACLs must not grant group/other
   write access or expose keys to unrelated accounts.
3. Select the approved backup location. The inventoried nightly directory is
   `/home/mikeshaggy/homelab/savix/backups/postgres`. SHG-16 rejects group/other
   writable directories; do not solve shared access with mode 0770/0777 or a
   write ACL that makes the group mode writable. A separate private Actions
   backup directory is simpler when preserving nightly ownership. In that case
   separately adopt retention/off-host copies for the new directory; SHG-17 does
   not modify nightly backups or delete either set of backups.
4. Make the selected state directory private and durable. Grant the runner access
   to the **existing** shared lock's private parent and read/write access to the
   lock without replacing its inode. Manual SHG-16 commands should run as the same
   deployment account (an administrator may use `sudo -u savix-runner`). If no
   lock exists yet, SHG-16 creates it at the agreed location, mode 0600, on the
   first separately approved mutating attempt. Do not touch/create one here to
   imply adoption occurred.

Configure these **repository Actions variables**, containing only paths/context
names; no Actions secrets are needed:

| Variable | Value after host review |
| --- | --- |
| `SAVIX_DOCKER_CONTEXT` | `default`; endpoint must be `unix:///var/run/docker.sock` |
| `SAVIX_RUNTIME_ENV` | Absolute mode-0600 runtime env path |
| `SAVIX_BACKUP_DIR` | Approved absolute private backup directory |
| `SAVIX_STATE_DIR` | Approved absolute private record directory |
| `SAVIX_LOCK_FILE` | Existing agreed shared lock path, identical to manual SHG-16 inputs |
| `SAVIX_PRODUCTION_ENABLED` | Leave unset/false until adoption; then `true` |

The job-level enable guard uses repository variables, not environment-only
variables. Restrict variable management to trusted production administrators.
Paths are non-secret configuration and can appear in SHG-16 progress messages;
choose paths without embedded credentials or sensitive customer identifiers.
No env contents, JWT material, DB passwords or proxy secrets belong in GitHub
variables, repository Actions secrets, summaries or uploaded artifacts.

## Install and register the ARM64 runner

Do this only during a separately authorized runner-adoption operation. Before
registration, secure the repository and keep `SAVIX_PRODUCTION_ENABLED` false.
Cancel obsolete queued runs before starting a new production runner.

In repository **Settings → Actions → Runners → New self-hosted runner**, select
**Linux / ARM64** for `mikeshaggy/savix-web-app`. Use the currently offered supported
runner release and SHA-256 from that page (not an old version copied from a blog).
The checkout action uses Node 24, so keep the runner current; never disable its
automatic updates. Follow [GitHub's repository registration instructions](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners).

As the administrator, open a shell as the new account:

```bash
sudo -iu savix-runner
cd /opt/savix-actions-runner
umask 077
# Enter the exact release version and Linux ARM64 checksum displayed by GitHub.
read -r -p 'Runner version: ' RUNNER_VERSION
read -r -p 'Linux ARM64 SHA-256: ' RUNNER_SHA256
[[ "$RUNNER_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 1
[[ "$RUNNER_SHA256" =~ ^[a-f0-9]{64}$ ]] || exit 1
curl --fail --location --proto '=https' --tlsv1.2 \
  --output runner.tar.gz \
  "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-arm64-${RUNNER_VERSION}.tar.gz"
printf '%s  runner.tar.gz\n' "$RUNNER_SHA256" | sha256sum --check - || exit 1
tar xzf runner.tar.gz
exit
# Install runner OS libraries as administrator; this does not install Java/Node toolchains.
sudo /opt/savix-actions-runner/bin/installdependencies.sh
sudo -iu savix-runner
cd /opt/savix-actions-runner
# Interactive config prompts for the short-lived repository registration token.
# Paste it only at the hidden token prompt; do not pass it in argv or save it.
./config.sh --url https://github.com/mikeshaggy/savix-web-app \
  --name savix-production-arm64 --labels savix-production \
  --work /var/lib/savix-runner/work
exit
```

Keep the automatic OS/architecture/default `self-hosted` labels. Verify in GitHub
that the runner has `self-hosted`, `Linux`, `ARM64`, `savix-production` (label
matching is case-insensitive). Do not use `--replace` to take over an existing
runner registration. Keep registration tokens and the runner's `.credentials*`
files private. The account needs outbound HTTPS for GitHub, runner updates, Maven,
npm and public base images. SHG-17 has no private registry or SSH deployment key.

Verify from a fresh runner account session before installing/starting the service:

```bash
sudo -iu savix-runner bash -lc '
  docker --context default context inspect default --format "{{.Endpoints.docker.Host}}"
  docker --context default info
  docker --context default compose version
  docker --context default buildx version
  git --version
  python3 --version
'
```

Review `docker info` privately on the host; do not paste it into Actions summaries.
The endpoint must be the local Unix socket, Docker must report Linux ARM64, and
Python must be 3.9+. Compose must satisfy SHG-16; use Compose 2.24.4+ and a current
Buildx with the daemon's `default` builder. Do not use a remote daemon or point
Docker environment variables at another context. A login-only PATH fix is not
sufficient: the service must find Docker/Compose/Buildx/Git/Python too.

Install systemd using the [runner service interface](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/configure-the-application):

```bash
cd /opt/savix-actions-runner
sudo ./svc.sh install savix-runner
# svc.sh writes the exact unit name to .service.
RUNNER_SERVICE=$(cat .service)
sudo systemctl enable "$RUNNER_SERVICE"
sudo systemctl edit "$RUNNER_SERVICE"
```

In the systemd override editor, set:

```ini
[Unit]
After=docker.service network-online.target
Wants=network-online.target

[Service]
UMask=0077
WorkingDirectory=/opt/savix-actions-runner
ExecStartPre=/usr/bin/docker --context default info --format {{.OSType}}/{{.Architecture}}
ExecStartPre=/usr/bin/docker --context default compose version
ExecStartPre=/usr/bin/docker --context default buildx version
ExecStartPre=/usr/bin/git --version
ExecStartPre=/usr/bin/python3 --version
```

Check `command -v` as the runner account and adjust the absolute executable paths
if the host installs them elsewhere. These checks run as the service account and
make service startup fail if access differs from a login shell. Keep runtime
secrets out of this unit. Then, only when explicitly authorized to start it:

```bash
sudo systemctl daemon-reload
sudo ./svc.sh start
sudo ./svc.sh status
sudo systemctl is-enabled "$RUNNER_SERVICE"
sudo systemctl show "$RUNNER_SERVICE" -p User -p Group -p UMask -p WorkingDirectory
```

Verify GitHub shows the runner online and idle, and later confirm automatic start
after an approved reboot. Do not reboot a production host just to complete SHG-17.
If the host uses `needrestart`, follow GitHub's service guide to exclude the runner
from automatic mid-job restarts. Coordinate OS/Docker maintenance with deployments.

## Containerized validation and immutable images

[scripts/ci/production-build.sh](../scripts/ci/production-build.sh) handles only CI
validation and local image creation. Host Java and Node installations are not
needed. The helper reads the pinned Java toolchain from the fixed backend
Dockerfile. A temporary container runs as the runner UID/GID with socket group
access, a writable repository mount at the identical path, private `/tmp` Maven
home and Linux host networking:

```text
./mvnw -B -ntp verify -Ppostgres-it
```

The full repository mount includes SHG-15's accepted catalog fixtures under
`docs/database`. Testcontainers gets only the local Docker socket, uses disposable
PostgreSQL with random mapped ports, and keeps Ryuk enabled. Loopback host override
plus Linux host networking satisfies SHG-15's local-only migration target rule.
See [Testcontainers containerized CI](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/).
No production env file, JWT directory or production container is passed to tests.
The helper cleans only its uniquely named validation container on exit; Ryuk owns
its test resources. SIGKILL/host loss can leave resources requiring inspection.

Frontend validation builds the existing Dockerfile's `build` target. Its commands
are exactly `npm ci`, `npm run lint`, `npm run build`; any nonzero exit blocks both
release-image steps. The final frontend build reuses that builder cache. Backend
image construction repeats the Dockerfile's ordinary `clean verify` because the
fixed Dockerfile excludes host output; the full PostgreSQL suite already passed
before either release image is produced. Do not weaken these fixed build gates
just to optimize Pi runtime. Independent stages/components are never launched in
parallel by Actions; internal compiler/BuildKit activity may still use multiple cores.

Release refs are `savix-backend:git-<full 40-character event SHA>` and
`savix-frontend:git-<full 40-character event SHA>`. Checkout HEAD must match that
SHA. Builds use `--platform linux/arm64 --load --build-arg VCS_REF=<SHA>` and the
existing component-specific, allowlisted build contexts. No production secrets
are build args. After building, local image OS/architecture, OCI revision label
and resulting tag/image ID are checked.

An existing valid SHA tag is verified and reused, never rebuilt/overwritten.
An existing tag with a wrong revision/architecture fails closed. A new image is
built without the final tag, validated by ID, and only then tagged; if a different
image appeared at that tag during the build, publishing fails. All release-tag
writers must follow this contract: the workflow is the sole automated writer,
and operators must never manually retag these names. Docker offers no atomic
compare-and-set/immutable-tag enforcement; a malicious/concurrent Docker admin
can defeat any workflow check. SHA labels assert provenance by trusted producers,
not cryptographic attestations. Base/dependency drift is handled by a new reviewed
commit; do not delete/rebuild a SHA tag to force different content under it.

## SHG-16 invocation and results

Both preflight and deployment pass the same quoted explicit backend/frontend
refs, runtime env, backup directory, state directory, shared lock and Docker
context into `./scripts/deploy.sh`. SHG-16 defaults to project `savix`. No Compose
override is supplied. The mutating call reacquires the host lock and reruns
preflight; the earlier read-only check is not a reservation or lock bypass.

| Failure | Behavior |
| --- | --- |
| Backend/frontend validation fails | Stop before release images or deployment; only isolated test/build resources change |
| Image build or image identity fails | Stop before preflight/deployment; cached/local build images may remain |
| SHG-16 read-only preflight fails | No backup, migration, rollout or deployment record mutation |
| SHG-16 mutating call fails | Workflow fails; SHG-16 reports its stage/result and any retained record location |
| Cancellation / timeout / lost runner | Inspect private records, running app IDs and migration containers before any retry |

Tests/builds do not intentionally mutate production application containers or
data, but consume CPU/RAM/disk on the live host. Tune operational capacity after
observing the first approved run. No automatic second deployment, Flyway repair,
Flyway clean, database restore, or independent Actions rollback is provided.
Use [SHG-16 recovery](deployment-orchestration.md#manual-application-rollback).

The always-run summary contains event SHA, intended image refs, validation/build
outcomes, preflight result and deployment result. A failed/skipped image step
means the displayed ref may not exist. The SHG-16 deployment step prints safe
stage and record locations; private records/logs remain on the host. No full
Compose config, env files, JWT material, application logs or backups are uploaded.
Summary delivery itself is best effort if the runner is lost or forcibly killed.

## Security model and first-production-adoption checklist

**Docker socket/group access effectively grants host-level production control.**
The dedicated account, non-root test process, read-only GitHub token and isolated
workspace reduce accidents; they do not sandbox trusted repository code away
from production. Maven plugins, npm scripts, Dockerfiles, dependencies and workflow
actions execute with that trust. Anyone able to alter executable code reaching
this runner is effectively a production administrator.

Before enabling anything:

- [ ] Review the diff and its pinned checkout action. Protect `main`, disable
  force pushes, require trusted review/checks, and review changes to workflows,
  build scripts, Dockerfiles and package dependencies as production changes.
- [ ] Configure GitHub environment `production` to allow only branch `main`.
  Optional required reviewers add a human gate to each release; choose this
  deliberately if fully automatic pushes are not yet appropriate.
- [ ] Audit **all** repository workflows and Actions/fork policies. Never route
  PR, fork or other untrusted branch code to this production runner; use GitHub
  hosted/non-production runners for PR checks. Never approve a fork workflow
  targeting this runner. Default/custom labels are routing, not authorization.
  A repository-scoped runner cannot enforce workflow/branch isolation against
  another permitted workflow choosing its labels. Do not register it for a repo
  whose contributor/Actions policy permits such execution; resolve that policy
  first. GitHub recommends private repositories for self-hosted runners; see
  [runner security guidance](https://docs.github.com/en/actions/concepts/security/secure-use#self-hosted-runners).
- [ ] Reconcile the accepted SHG-12 infrastructure inventory and SHG-13 runtime
  configuration; preserve live checkout assets, existing keys and data volumes.
- [ ] Provision/review account permissions, private paths, shared lock access,
  daemon access, dependency network access and runner work isolation.
- [ ] Complete the separately reviewed **manual production baseline adoption**
  using [SHG-14](database-migrations.md#one-time-existing-production-adoption-manual-not-performed-in-shg-14).
  SHG-17 never performs baseline automatically, and ordinary deployment rejects
  an untracked populated schema.
- [ ] Review backup capacity, retention, full restore rehearsal, off-host copies,
  prior local app images, and forward-schema compatibility for manual recovery.
- [ ] Separately authorize runner installation/registration/service startup;
  verify service-account tools, labels, work directory and reboot persistence.
- [ ] Leave deployment disabled while checking for obsolete pending runs and
  confirming the desired first candidate SHA. Enable the repository variable only
  when a first-production-deployment operation is explicitly authorized.
- [ ] Make the approved push to `main`; observe the first validation, image build,
  preflight and deployment, inspect private release/success records, and verify
  external traffic separately. Record adoption outcome outside this implementation
  ticket. Future reviewed `main` pushes then follow the same workflow automatically.

## Troubleshooting and follow-ups

- **Job skipped:** inspect the repository/ref/event and repository enable variable.
  It intentionally skips while adoption is disabled. There is no dispatch button.
- **Job waiting:** check all four labels, runner online/service state, concurrency
  and environment rules/reviewers. A new pending push may supersede an older one.
- **Workspace guard:** confirm registration's absolute `--work` path and no
  symlink to production. Do not relax the guard to accommodate a live checkout.
- **Docker permission/context failure:** check a fresh service account's groups,
  service PATH, Unix socket ownership and `default` context. Do not chmod the
  socket world-writable, enable remote Docker or grant passwordless sudo.
- **PostgreSQL tests cannot connect:** verify the local Linux socket, host networking,
  random mapped ports, pinned PostgreSQL/Ryuk downloads and DNS/network access.
  Do not point tests at production, skip integration tests or disable Ryuk to pass.
- **Build timeout/out-of-space:** inspect host capacity and retained image/cache
  growth. Preserve current/previous images and failed attempt records. Schedule
  reviewed cleanup; never run broad image/volume prune from Actions.
- **Wrong SHA/architecture/tag collision:** inspect the tag's provenance privately;
  do not overwrite it. Fix the producer and use a new reviewed commit/release ref.
- **Preflight/deployment failed:** follow SHG-16's stage-specific troubleshooting
  and recovery. Runtime credential consistency, existing app images, key access,
  external resources and baseline adoption are host prerequisites, not YAML fixes.

Outstanding operational work: runner adoption, baseline adoption, first deployment,
restore/off-host-backup policy, measured Pi resource budget, dependency/action/runner
update policy, and controlled image/cache retention. A persistent production runner
is a large trust boundary; if repository policy cannot exclude untrusted execution,
separate build infrastructure is a future architecture decision, not SHG-17 scope.

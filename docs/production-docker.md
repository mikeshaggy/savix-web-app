# Production Docker setup (SHG-12)

This versions the Docker build procedure and the six-service Raspberry Pi topology
recorded by SHG-11 on 2026-09-15. Source baseline: `8a11f91` on the existing
`shg-11-ci-cd-flyway` worktree. The private investigation and sanitized inventory
remain in `.local/shg-11/`; this document records the operational contract needed
by future work without publishing private evidence or credentials.

**No deployment is part of SHG-12.** No production resources have been modified.
The manifest is for adoption of the existing installation, not fresh provisioning.

## Build images

Run from the repository root on a local Docker daemon. The Pi uses `linux/arm64`;
these commands also select ARM64 explicitly on other hosts:

```sh
docker build --platform linux/arm64 --build-arg VCS_REF=<source-revision> \
  -t savix-backend:<candidate-tag> backend
docker build --platform linux/arm64 --build-arg VCS_REF=<source-revision> \
  -t savix-frontend:<candidate-tag> frontend
docker image inspect savix-backend:<candidate-tag> savix-frontend:<candidate-tag> \
  --format '{{.RepoTags}} {{.Os}}/{{.Architecture}} user={{.Config.User}}'
```

Build sequentially on the Pi to limit resource pressure. Both Dockerfiles use
multi-stage builds and digest-pinned multi-architecture base images. Updating a
base is a deliberate reviewed change. They use the target architecture throughout;
no AMD64-only base or host `target`, `node_modules`, or `.next` is copied.

- Backend: Java 21 JDK build, repository Maven wrapper 3.9.10, `clean verify`
  including the existing tests; Java 21 JRE runtime, non-root `spring` UID/GID
  10001, executable JAR at `/app/app.jar`, port 8000. Maven is downloaded by the
  wrapper, not supplied by the build image.
- Frontend: production-compatible Node **22** Alpine in both stages, lockfile
  installation with `npm ci`, `npm run lint`, `npm run build`, then Next.js
  standalone `server.js`, static assets and public files; non-root `nextjs`
  UID/GID 1001, listening on `0.0.0.0:3000`.
- `VCS_REF` records the source revision label (defaults to `unknown` for ad hoc
  builds). Image names/tags are selected by the caller. Compose requires explicit
  `BACKEND_IMAGE` and `FRONTEND_IMAGE`, ready for later immutable references.
  SHG-12 does not implement a release/tagging workflow or a registry.
- `NEXT_PUBLIC_API_BASE_URL` is the frontend's optional **public build argument**,
  default `/api/proxy`. Never use it for a secret. `PROXY_BASE` and `PROXY_SECRET`
  are supplied to the running server, with no production `.env` baked into it.

Builds require access to container registries, Maven Central, npm and Google Fonts
(the existing Next.js font loader). Pinning bases and using the Maven wrapper and
npm lockfile makes the build inputs repeatable; this does not promise byte-identical
artifacts, offline builds, or pinned external Google Fonts responses.

Each service context denies files by default and allows only build inputs. Nested
`.env` files, key material, credential dumps, local Spring overrides, `.git`,
`.local`, logs, dependencies and build outputs are excluded. The root `.dockerignore`
rejects all files so accidentally building with the repository root fails closed.
New build inputs must be deliberately added to the appropriate allowlist.
See [Docker build contexts](https://docs.docker.com/build/concepts/context/).

## Production Compose contract

Use **only** `compose.production.yml`, with explicit project `savix`. Do not merge
it with the development `docker-compose.yml`: that file exposes local credentials,
uses a different network and includes a database initialization bind mount.
Do not override the project to `savix-app` or create a parallel Compose project.

| Service | Container | Published host → container | Networks |
| --- | --- | --- | --- |
| `savix-backend` | `savix-backend` | `8000:8000` | `savix_savix_internal`, `monitoring_shared` |
| `savix-frontend` | `savix-frontend` | `3000:3000` | `savix_savix_internal` |
| `savix-db` | `savix-db` | `5432:5432` | `savix_savix_internal` |
| `savix-redis` | `savix-redis` | `6379:6379` | `savix_savix_internal` |
| `postgres-exporter` | `savix-postgres-exporter` | none (internal 9187) | `savix_savix_internal`, `monitoring_shared` |
| `redis-exporter` | `savix-redis-exporter` | none (internal 9121) | `savix_savix_internal`, `monitoring_shared` |

All use `unless-stopped`. Published ports retain all-interface binding. Service and
container DNS aliases are preserved automatically; ephemeral container IPs are not
pinned. Both existing bridge networks have `Internal=false`. Their existing
subnets (`172.22.0.0/16` and `172.21.0.0/16`) are retained through external lookup,
not re-created with new IPAM declarations.

Persistent resources:

- PostgreSQL: exact existing `savix_savix-db-data` → `/var/lib/postgresql/data`,
  read-write; database `savix`, existing user `savix-admin`. No init SQL mount.
- Redis: exact existing anonymous volume
  `30e5abda8a6c7b41a64cc8363266d09a1424d4a025800857f72dd636e0a2eb5d`
  → `/data`, read-write. The new logical Compose name
  `savix-redis-existing-data` references this same volume; it does not copy,
  migrate, rename or allocate Redis storage. No authentication, AOF, save-policy,
  command or health-check change is introduced.

Both volumes and both networks are external, with literal names, so an absent
resource causes an error rather than creation of an empty substitute.
[Docker external volumes](https://docs.docker.com/reference/compose-file/volumes/#external)
are used only as references here. This declaration changes lifecycle management
in the manifest; it does not mutate existing resources or their labels.

Infrastructure image defaults retain the observed references: `postgres:16-alpine`
(installed 16.11), `redis:alpine` (installed 8.4.0), and the two exporter `latest`
tags. `pull_policy: never` prevents implicit downloads/upgrades. These tags alone
are **not immutable**: later adoption must verify the installed image IDs and set
explicit image references before any replacement. Exporter versions/digests were
not established by the inventory and are not guessed here.

Health commands and timings retain the inventory: backend Actuator HTTP probe
(30s/3s/40s start/3 retries), frontend root-page Node fetch (30s/3s/30s start/3),
PostgreSQL `pg_isready` (10s/5s/10). The frontend root probe does not establish
end-to-end readiness; its improvement belongs to later work. Redis and exporters
have no health checks. Startup dependency ordering was not recorded in the
inventory, so no new `depends_on` or rollout behavior is inferred.

Cloudflare remains the host `cloudflared.service`, with the existing origin
`http://localhost:3000`. Monitoring and the nightly PostgreSQL backup job are
outside this manifest and unchanged.

## Runtime configuration and existing keys

`docs/production.env.example` lists the configuration names. Required values are
empty intentionally; it is documentation, not usable production configuration.
The schema validation below uses dummy values only. Keep real values outside Git
and build contexts. No production secret has been read into this implementation.

Before later adoption, an operator must supply **existing** values:

- Database password shared by PostgreSQL and backend; preserve the current user
  and database. Changing the initialization variables does not change a password
  or database in an existing PostgreSQL volume.
- Full existing PostgreSQL exporter DSN for `savix-db:5432/savix`, including its
  credentials, URI escaping and options. It remains a separate runtime placeholder
  to avoid unsafe URI construction from a raw password.
- Existing SMTP host, username/password, sender address/name, frontend origin,
  JWT issuer/audience, and cookie domain (empty if currently unset).
- Existing proxy secret, injected from the same variable into backend and frontend;
  frontend `PROXY_BASE` stays `http://savix-backend:8000`.
- Absolute `JWT_PRIVATE_KEY_FILE` and `JWT_PUBLIC_KEY_FILE` host paths to the
  **current** key pair. The inventory found these keys packaged inside the running
  backend JAR. Preserving/extracting them and provisioning protected host files is
  deliberately deferred to SHG-13. Do not generate replacements. New images contain
  no key pair and cannot start production authentication until these files exist.

Compose mounts those files read-only at `/run/savix/jwt-private-key.pem` and
`/run/savix/jwt-public-key.pem`, and sets Spring's existing `JWT_*` resource options
to `file:` locations. Host files must be readable by UID/GID 10001 (or equivalent
ACL access) without broadening access to the private key. `create_host_path: false`
prevents a missing file being silently replaced by an empty directory.
No source configuration, secret store, key rotation, roles or credentials are
changed in SHG-12.

## Validation without deployment

On the validation machine only, set dummy runtime variables in the shell or in an
ignored `.local/shg-12/compose-validation.env`, including dummy absolute key paths.
Key files and external resources need not exist for schema-only validation.

```sh
docker compose -p savix --env-file .local/shg-12/compose-validation.env \
  -f compose.production.yml config --quiet
docker compose -p savix --env-file .local/shg-12/compose-validation.env \
  -f compose.production.yml config --services
```

`config --quiet` avoids printing interpolated credentials when real configuration
is used later. Never save or publish a fully interpolated production manifest.
Do not run `up`, `down`, `--remove-orphans`, volume cleanup, or any production
container recreation for this task. No full-stack adoption is authorized.

## Differences and deferred work

The running Pi images come from an older dirty checkout with no revision labels;
these builds use the SHG-11 source and committed npm lockfile. Java 21 and Node 22
majors are retained; newly pinned base patch versions may differ from the running
images, whose exact runtime patches were not inventoried. New images run tests and
lint during builds and carry an optional revision label. The npm lockfile adds
one missing optional `next-intl` peer entry (`@swc/helpers` 0.5.23), required by
Node 22/npm for a clean `npm ci`; existing locked versions and `package.json`
are unchanged.

The production manifest now references existing resources externally, explicitly
references Redis's existing anonymous data volume, requires selected app images,
and disables implicit pulls. Keys become read-only runtime mounts and the frontend
proxy configuration becomes runtime environment; these are the minimum packaging
changes required to keep secrets out of new artifacts. They require later operator
preparation before adoption. No production change has occurred.

Deferred: full secret externalization and key provisioning (SHG-13), Flyway,
baseline/schema reconciliation (SHG-14 / later), health/readiness improvements,
immutable release automation, deployment scripts, GitHub Actions, runner setup,
backups/restore rehearsal, rollout/rollback and first production adoption. No
application or SQL behavior is changed here.

## SHG-12 validation result (2026-09-15)

Validated locally on native ARM64 Docker Desktop (`desktop-linux`, Compose 5.1.3),
using `--platform linux/arm64`. Nothing was built or run on the production Pi.

| Check | Result |
| --- | --- |
| Backend Docker build / Maven `clean verify` | PASS: 702 tests, zero failures/errors/skips |
| Frontend Docker build / Node 22 `npm ci`, lint, build | PASS: 32 static pages generated |
| Runtime architecture and user | Both `linux/arm64`; backend UID 10001, frontend UID 1001 |
| Runtime versions | Java 21.0.12; Node 22.23.2 |
| Compose validation | PASS: config, six service/container identities, ports, networks, mounts, proxy configuration and 13 required-value guards |
| Context exclusion checks | PASS: 12 backend and 11 frontend harmless private-file markers excluded; every exported context input was a repository source file |
| Image contents | Backend JAR has no PEM/private keys or local Spring config; frontend `/app` has no `.env` or key files |
| Isolated frontend smoke test | Login and static asset HTTP 200; health and runtime proxy forwarding pass against a local mock backend with networking disabled |
| Changed-file review | No private-key material or production credential literals; existing lock entries unchanged |

The frontend retains the pre-existing `src/utils/helpers.js:79` lint warning.
`npm ci` reports the same 14 dependency vulnerabilities noted in SHG-11; dependency
remediation is outside this Docker task. Production authentication, mail, database
connectivity and first adoption were not exercised with real credentials.

Validation images remain local as `savix-backend:shg12-validation` and
`savix-frontend:shg12-validation`. They describe the uncommitted SHG-12 worktree,
not an immutable production release. Detailed local evidence is under
`.local/shg-12/` and remains ignored by Git.

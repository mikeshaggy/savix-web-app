# Production runtime secrets (SHG-13)

SHG-12 is the Docker/topology foundation. SHG-13 changes packaging/configuration
only. **No production host changes or deployment have been performed.** The
following adoption procedure is for a separately authorized maintenance window;
it is not an instruction to deploy now. Never rotate or regenerate credentials.

## Runtime contract

| Setting | Contract |
| --- | --- |
| `JWT_PRIVATE_KEY_FILE`, `JWT_PUBLIC_KEY_FILE` | Absolute paths on the Docker host, outside any checkout, build context, or CI workspace; existing PKCS#8 private / X.509 public PEM files. |
| `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` | Backend `file:` resource URIs. Compose sets `/run/savix/jwt-private-key.pem` and `/run/savix/jwt-public-key.pem`. `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` remain supported aliases. |
| `PROXY_BASE` | Frontend runtime environment only; preserve `http://savix-backend:8000` for the existing topology. |
| `PROXY_SECRET` | Existing shared value supplied at runtime to both services from one source. Never `NEXT_PUBLIC_*`, Next.js `env`, a build argument, or a Dockerfile `ENV`. |
| `JWT_ISSUER`, `JWT_AUDIENCE`, cookie settings | Preserve existing values exactly, along with the existing database and Redis/session state. |

The backend runs as UID/GID `10001:10001`. Bind mounts are read-only and have
`create_host_path: false`. Production accepts only filesystem resources, fails
on missing/unreadable/malformed/mismatched/non-P-256 keys, and logs only the public
key's SHA-256 DER fingerprint after successfully validating the pair. It does not
assign a new JWT `kid`. Even `prod,dev` uses the existing files; only `dev` without
`prod` may generate ephemeral development keys. The old key-generation helper is
disabled. Neither Dockerfile nor the startup command generates a production pair.

The frontend proxy and health routes read server environment variables inside
request handlers and force dynamic execution. The same built image can run with
different proxy environments; no secret is required to build it. Docker contexts
exclude env files, secret/key directories, key formats, local output and private
folders. Maven also excludes key/env/local override resources from non-Docker JAR
builds. These exclusions are a second barrier, not permission to put credentials
in source files under other names.

## Later adoption procedure

Prerequisites: an approved maintenance/rollback plan, reviewed candidate images,
Python 3 and OpenSSL on the host, and access to the existing runtime configuration.
Use a private root shell on the Pi with terminal recording disabled. Do not use
shell tracing, `printenv`, unrestricted `docker inspect`, interpolated
`docker compose config`, `unzip -p` to the terminal, or copy secret contents into
commands. Preserve the existing images and containers until checks pass.

### 1. Preserve the deployed pair before replacing any container

Choose an operator-owned absolute directory outside the repository and all build
contexts. The paths below are variables, not production host-path defaults. Set
`SECRET_DIR` to that approved directory and `RUNTIME_ENV` to a protected file
outside the checkout. Run the remaining blocks in the same private Bash shell.

```bash
set +x
set -euo pipefail
umask 077
: "${SECRET_DIR:?Set the approved absolute host secret directory}"
: "${RUNTIME_ENV:?Set the approved absolute runtime environment file path}"
case "$SECRET_DIR" in /*) ;; *) exit 1 ;; esac
case "$RUNTIME_ENV" in /*) ;; *) exit 1 ;; esac
install -d -o root -g 10001 -m 0750 "$SECRET_DIR"
PRESERVE_DIR=$(mktemp -d "$SECRET_DIR/preserve.XXXXXXXX")
export SECRET_DIR RUNTIME_ENV PRESERVE_DIR
# The SHG-11 inventory located the deployed executable here. No key output.
docker cp savix-backend:/app/app.jar "$PRESERVE_DIR/deployed.jar"
chmod 0600 "$PRESERVE_DIR/deployed.jar"
python3 - <<'PYTHON'
import os, pathlib, zipfile
folder = pathlib.Path(os.environ['PRESERVE_DIR'])
with zipfile.ZipFile(folder / 'deployed.jar') as jar:
    for name in ('jwt-private-key.pem', 'jwt-public-key.pem'):
        data = jar.read('BOOT-INF/classes/' + name)
        assert data, 'Missing deployed JWT resource; stop adoption'
        with (folder / name).open('xb') as output:
            output.write(data)
        (folder / name).chmod(0o600)
print('Existing key files preserved; contents not displayed.')
PYTHON
```

If either JAR entry is missing, stop and investigate the current deployment.
Do not generate a replacement. The preserved JAR itself contains production
secrets: keep it only in this protected directory or an approved encrypted backup,
never in Git, image registries, build caches, or CI artifacts.

### 2. Validate, fingerprint and install the same bytes

```bash
openssl pkey -in "$PRESERVE_DIR/jwt-private-key.pem" -check -noout
openssl pkey -in "$PRESERVE_DIR/jwt-private-key.pem" -pubout -outform DER   -out "$PRESERVE_DIR/derived-public.der"
openssl pkey -pubin -in "$PRESERVE_DIR/jwt-public-key.pem" -outform DER   -out "$PRESERVE_DIR/original-public.der"
cmp -s "$PRESERVE_DIR/derived-public.der" "$PRESERVE_DIR/original-public.der"
openssl dgst -sha256 "$PRESERVE_DIR/original-public.der"   > "$PRESERVE_DIR/public-key-fingerprint.txt"
# Refuse to overwrite previously provisioned key files.
test ! -e "$SECRET_DIR/jwt-private-key.pem"
test ! -e "$SECRET_DIR/jwt-public-key.pem"
install -o root -g 10001 -m 0440 "$PRESERVE_DIR/jwt-private-key.pem" "$SECRET_DIR/jwt-private-key.pem"
install -o root -g 10001 -m 0440 "$PRESERVE_DIR/jwt-public-key.pem" "$SECRET_DIR/jwt-public-key.pem"
cmp -s "$PRESERVE_DIR/jwt-private-key.pem" "$SECRET_DIR/jwt-private-key.pem"
cmp -s "$PRESERVE_DIR/jwt-public-key.pem" "$SECRET_DIR/jwt-public-key.pem"
```

Both files stay root-owned and readable only by root/the backend group. Restrict
membership of host GID 10001; verify any rootless Docker/user-namespace mapping
before using these numeric IDs. The directory is 0750 and the preservation
subdirectory is 0700. Do not solve permission failures with world-readable modes.
The public fingerprint is safe to compare; private key contents are never needed
on screen. Existing provisioned files may only be reused after byte comparison,
not overwritten blindly.

### 3. Preserve and configure runtime values

Start from `docs/production.env.example` in the protected location; populate all
existing SHG-12 settings with a private editor/secret manager, never shell-history
literals. Keep this file root-owned, mode 0600, and its parent protected. Set
`JWT_PRIVATE_KEY_FILE` and `JWT_PUBLIC_KEY_FILE` to the installed absolute paths.
Set the approved candidate image references without changing issuer, audience,
cookie settings, SMTP, database credentials, networks, or persistent volumes.

The deployed backend environment already contains the shared proxy secret; this
block copies it without printing it, using exclusive file creation and literal
Compose quoting. It also preserves issuer/audience. Run it once after setting the
other required values in a new template copy; it refuses a populated secret.

```bash
python3 - <<'PYTHON'
import json, os, pathlib, subprocess
path = pathlib.Path(os.environ['RUNTIME_ENV'])
if not path.exists():
    # Run from the reviewed checkout; this file contains no real values.
    template = pathlib.Path('docs/production.env.example').read_text()
    with path.open('x') as output:
        output.write(template)
path.chmod(0o600)
text = path.read_text()
values = dict(item.split('=', 1) for item in json.loads(subprocess.check_output(
    ['docker', 'inspect', '--format', '{{json .Config.Env}}', 'savix-backend'],
    text=True)) if '=' in item)
updates = {name: values[name] for name in ('PROXY_SECRET', 'JWT_ISSUER', 'JWT_AUDIENCE')}
updates.update(PROXY_BASE='http://savix-backend:8000',
               JWT_PRIVATE_KEY_FILE=os.environ['SECRET_DIR'] + '/jwt-private-key.pem',
               JWT_PUBLIC_KEY_FILE=os.environ['SECRET_DIR'] + '/jwt-public-key.pem')
lines = text.splitlines()
assert 'PROXY_SECRET=' in lines, 'Stop: runtime secret is already populated'
for name, value in updates.items():
    assert value and not any(c in value for c in "'\r\n"), 'Use a secret manager for unsupported env-file characters'
    matches = [i for i, line in enumerate(lines) if line.startswith(name + '=')]
    assert len(matches) == 1, 'Expected exactly one runtime setting'
    lines[matches[0]] = name + "='" + value + "'"
path.write_text('\n'.join(lines) + '\n')
path.chmod(0o600)
print('Existing runtime values preserved; contents not displayed.')
PYTHON
chown root:root "$RUNTIME_ENV"
chmod 0600 "$RUNTIME_ENV"
docker compose --env-file "$RUNTIME_ENV" -f compose.production.yml config --quiet
```

Single quotes prevent Compose interpolation of dollar signs in existing values.
For values with quotes/newlines, stop and use an approved secret manager/runtime
environment injection preserving exact bytes. Do not invent a substitute. The
frontend used packaged configuration before adoption; SHG-11 verified that its
secret equals the backend value. Retain that value for both new containers.

### 4. Verify mount access before adoption

Set `BACKEND_IMAGE` in the private shell to the same selected candidate reference.
This check uses the image's actual non-root user, has no network, and does not
start the application or display key bytes:

```bash
docker run --rm --network none --entrypoint sh   --mount "type=bind,src=$SECRET_DIR/jwt-private-key.pem,dst=/run/savix/jwt-private-key.pem,readonly"   --mount "type=bind,src=$SECRET_DIR/jwt-public-key.pem,dst=/run/savix/jwt-public-key.pem,readonly"   "$BACKEND_IMAGE" -ec 'test -r /run/savix/jwt-private-key.pem; test -r /run/savix/jwt-public-key.pem; test ! -w /run/savix/jwt-private-key.pem; test ! -w /run/savix/jwt-public-key.pem'
```

### 5. Adopt only under the later approved rollout, then check identity

The actual rollout/rollback command belongs to the later deployment task. Do not
run Compose `up`/`down` as part of SHG-13. Immediately before that rollout, keep a
browser logged in as an existing account; preserve its cookies without exporting
or logging them. Record the expected public fingerprint from step 2.

After the approved container replacement:

1. Confirm both mounts report `RW=false` using targeted mount inspection only:
   `docker inspect --format '{{range .Mounts}}{{.Destination}} read-write={{.RW}}{{println}}{{end}}' savix-backend`.
2. Compare the backend log line `Loaded existing JWT key pair; public key SHA-256:`
   with the SHA-256 in the protected fingerprint file. Only compare/display that
   public fingerprint. Absence or mismatch is a stop/rollback condition.
3. Confirm health and proxy requests succeed, and that there is no
   `USING DEVELOPMENT JWT KEYS` log entry. Do not print environment/headers.
4. Using the previously logged-in browser within the current access-token lifetime,
   open a protected page. Then verify its existing refresh session still renews
   authentication after access-token expiry. Keep the original database and Redis
   state; identical signing keys alone do not preserve deleted refresh sessions.
5. Check a fresh login and logout with an existing account. Verify protected data
   access and cookie flags/domain. Do not record cookies, tokens or passwords in
   screenshots, HAR files, test output or Actions logs.
6. Use a controlled request with a dummy incorrect proxy header to verify direct
   backend API access returns 403. Never send the real secret from the browser.

Any fingerprint/authentication failure means stop adoption and follow the approved
rollback plan using preserved images/configuration. Do not regenerate keys to
repair authentication. Retire secret-bearing legacy images/JARs/caches only under
the later approved retention/rollback policy after continuity is established.

## Future CI/logging contract

No GitHub Actions workflow is introduced in SHG-13. Future build/test jobs must
have no production secret access, use only dummy secrets, and send only the
service contexts to Docker. Never pass runtime secrets using build arguments,
Dockerfile environment, public Next variables, or `next.config` environment.
Deployment credentials/files must stay out of the build workspace and artifacts.
Use `docker compose config --quiet`, no shell tracing, no environment dumps,
request header/token logging or raw image inspection. Mask any runtime credential
before a future deployment job could log it, but do not rely on masking to make
secret-bearing builds safe. Only public fingerprints and pass/fail results belong
in evidence. Workflow enforcement belongs to the later CI subtask.

## Validation

Run backend `clean verify` and frontend lint/build through the SHG-12 Dockerfiles.
The JWT regression tests use temporary dummy pairs and cover reload continuity,
production/dev precedence, missing/malformed/wrong-curve/mismatched keys and local
development. The local runtime validation uses only dummy configuration; it must
not point at the Pi or existing production resources.

### Results (2026-09-15, local ARM64 Docker Desktop)

| Check | Result |
| --- | --- |
| Backend Docker build / Maven `clean verify` | PASS: 716 tests, zero failures/errors/skips; includes 14 new JWT configuration cases. |
| Frontend Docker build / lint / production build | PASS: 31 static pages; proxy and health routes dynamic. One pre-existing `helpers.js:79` lint warning remains. |
| Backend build and runtime image layers, config and nested JAR | PASS: no JWT key resources/material or dummy secret values. |
| Frontend build and runtime image layers, config and standalone output | PASS: no env files, excluded markers, or runtime dummy proxy secret. No production values were supplied to validation. |
| Docker build contexts | PASS: 12 nested secret-file canaries per service excluded; every exported file was an intentional repository source input. |
| Direct Maven packaging, bypassing Docker ignore | PASS: eight secret resource canaries excluded; nonsecret control resource retained. |
| Runtime JWT files | PASS: non-root production backend loads read-only mounts; fingerprint matches the original dummy pair. |
| Missing private/public file | PASS: each independently fails startup with a clear JWT error, including combined `prod,dev`. |
| Existing-token identity continuity | PASS: unit test reloads the same files and validates previously issued access and refresh tokens. |
| Runtime proxy against real backend | PASS: registration, login, refresh, logout, two HttpOnly cookies, and health; uses isolated H2 and Redis with dummy credentials. |
| Wrong/missing proxy settings | PASS: incorrect runtime secret returns 403; missing `PROXY_BASE` or `PROXY_SECRET` returns 500. Same frontend image used throughout. |
| Compose | PASS: schema, common runtime secret, configurable base, read-only bind mounts, disabled host-path creation. |
| Logs / Git diff | PASS: no dummy runtime proxy secret/private key values in application logs or Git diff; no production credential literals added. |

Evidence and the local validation harness remain ignored under `.local/shg-13/`.
The harness is limited to the local Docker Desktop socket and creates only
isolated temporary containers/networks. These were removed after validation;
images remain tagged `savix-backend:shg13-validation`,
`savix-frontend:shg13-validation`, and matching `*-build` validation tags. They
represent this uncommitted worktree, not an immutable production release.

The first full smoke check reached login/refresh/logout successfully but health
returned 503 because the test environment has no mail server. The focused rerun
disabled only that test container's mail health indicator and passed all checks.
Production health configuration is unchanged. Actual production key fingerprints,
mail connectivity and existing user sessions remain adoption-window checks; no
production secrets were fetched, extracted, modified or used here.

### Scope boundaries

SHG-14/Flyway/schema work, deployment/rollback automation, GitHub Actions and runner
setup, dependency upgrades, host provisioning, production adoption, and retirement
of secret-bearing legacy images/caches remain deferred. Existing production
artifacts still contain their original secrets until the later approved adoption
and retention work; SHG-13 prevents this in newly built artifacts.

Implementation references: [Next.js environment variables](https://nextjs.org/docs/app/guides/environment-variables)
and [Docker build-context exclusions](https://docs.docker.com/build/concepts/context/#dockerignore-files).

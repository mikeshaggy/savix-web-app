#!/usr/bin/env bash
# SHG-17 CI only. SHG-16 owns every production deployment operation.
set -Eeuo pipefail

fail() { printf 'ERROR: %s\n' "$1" >&2; exit 1; }
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
cd -- "$root"
[[ ${GITHUB_SHA:-} =~ ^[a-f0-9]{40}$ ]] || fail 'Expected a full Git commit SHA'
[[ $(git rev-parse HEAD) == "$GITHUB_SHA" ]] || fail 'Checkout does not match workflow SHA'
[[ $root == "${GITHUB_WORKSPACE:?}" ]] || fail 'Use only the runner-managed checkout'
[[ ${GITHUB_RUN_ID:-} =~ ^[0-9]+$ && ${GITHUB_RUN_ATTEMPT:-} =~ ^[0-9]+$ ]] || fail 'Invalid workflow run identity'
docker=(docker --context "${SAVIX_DOCKER_CONTEXT:?}")
# Containerized integration tests use the same local daemon as production builds.
endpoint=$("${docker[@]}" context inspect "$SAVIX_DOCKER_CONTEXT" --format '{{.Endpoints.docker.Host}}')
[[ $endpoint == unix:///var/run/docker.sock ]] || fail 'Runner requires the local /var/run/docker.sock daemon'

verify_image() {
  local reference=$1 actual
  actual=$("${docker[@]}" image inspect "$reference" --format '{{.Os}}/{{.Architecture}} {{index .Config.Labels "org.opencontainers.image.revision"}}')
  [[ $actual == "linux/arm64 $GITHUB_SHA" ]] || fail 'Image architecture/revision mismatch; immutable tag will not be replaced'
}

build_image() {
  local component=$1 reference="savix-$1:git-$GITHUB_SHA" image_id existing_id
  if "${docker[@]}" image inspect "$reference" >/dev/null 2>&1; then
    verify_image "$reference"
    printf 'Reusing verified immutable image: %s\n' "$reference"
    return
  fi
  iidfile=$(mktemp "${RUNNER_TEMP:?}/savix-image.XXXXXXXX")
  trap 'rm -f -- "$iidfile"' EXIT
  # The daemon context's built-in builder caches the verified frontend build stage.
  # Build without the immutable tag; do not touch it if the build fails.
  "${docker[@]}" buildx build --builder "$SAVIX_DOCKER_CONTEXT" --platform linux/arm64 --load \
    --build-arg "VCS_REF=$GITHUB_SHA" --iidfile "$iidfile" "$component"
  image_id=$(cat -- "$iidfile")
  [[ $image_id =~ ^sha256:[a-f0-9]{64}$ ]] || fail 'Build did not return a local image ID'
  verify_image "$image_id"
  if existing_id=$("${docker[@]}" image inspect "$reference" --format '{{.Id}}' 2>/dev/null); then
    [[ $existing_id == "$image_id" ]] || fail 'Immutable tag appeared with different content during build'
  else
    "${docker[@]}" image tag "$image_id" "$reference"
  fi
  verify_image "$reference"
  [[ $("${docker[@]}" image inspect "$reference" --format '{{.Id}}') == "$image_id" ]] || fail 'Image tag identity changed'
  printf 'Built and verified immutable image: %s\n' "$reference"
}

case ${1:-} in
  check)
    platform=$("${docker[@]}" info --format '{{.OSType}}/{{.Architecture}}')
    [[ $platform == linux/aarch64 || $platform == linux/arm64 ]] || fail 'Docker daemon must be Linux ARM64'
    "${docker[@]}" compose version
    "${docker[@]}" buildx version
    git --version
    docker_root=$("${docker[@]}" info --format '{{.DockerRootDir}}')
    # Read the Docker filesystem through its nearest searchable parent; no root access needed.
    python3 - "$root" "$docker_root" <<'PY'
import shutil
import sys
from pathlib import Path
for value in sys.argv[1:]:
    path = Path(value)
    if not path.is_absolute():
        raise SystemExit('Expected an absolute local storage path')
    while True:
        try:
            free = shutil.disk_usage(path).free
            break
        except PermissionError:
            if path == path.parent:
                raise
            path = path.parent
    if free < 8 * 1024**3:
        raise SystemExit('At least 8 GiB free required on workspace and Docker storage filesystems')
print('Local ARM64 Docker and build storage checks passed (8 GiB minimum).')
PY
    ;;
  verify-backend)
    # Match the fixed Dockerfile toolchain, including its reviewed digest.
    java_image=$(awk '$1 == "FROM" { print $2; exit }' backend/Dockerfile)
    [[ $java_image == *@sha256:* ]] || fail 'Backend toolchain must be digest-pinned'
    container="savix-ci-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}-backend"
    cleanup() { "${docker[@]}" rm -f "$container" >/dev/null 2>&1 || true; }
    trap cleanup EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
    # Linux host networking keeps the Testcontainers random ports on loopback,
    # satisfying SHG-15's local-only DB target contract. Ryuk stays enabled.
    "${docker[@]}" run --rm --platform linux/arm64 --name "$container" --network host \
      --user "$(id -u):$(id -g)" --group-add "$(stat -c '%g' /var/run/docker.sock)" \
      --mount "type=bind,src=$root,dst=$root" \
      --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock \
      --workdir "$root/backend" --env HOME=/tmp --env MAVEN_USER_HOME=/tmp/.m2 --env MAVEN_OPTS=-Duser.home=/tmp \
      --env DOCKER_HOST=unix:///var/run/docker.sock --env TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 \
      "$java_image" ./mvnw -B -ntp verify -Ppostgres-it
    ;;
  verify-frontend)
    # The fixed build stage runs npm ci, npm run lint, npm run build in order.
    # Its cache is reused when the runtime image is built after all tests pass.
    "${docker[@]}" buildx build --builder "$SAVIX_DOCKER_CONTEXT" --platform linux/arm64 --target build --load frontend
    ;;
  build-backend) build_image backend ;;
  build-frontend) build_image frontend ;;
  *) fail 'Usage: production-build.sh check|verify-backend|verify-frontend|build-backend|build-frontend' ;;
esac

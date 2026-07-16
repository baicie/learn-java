#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PREPARE_SCRIPT="$SCRIPT_DIR/prepare-docker.sh"
TMP_ROOT="$(mktemp -d)"
MOCK_BIN="$TMP_ROOT/bin"
CALL_LOG="$TMP_ROOT/calls.log"

cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

mkdir -p "$MOCK_BIN"

cat >"$MOCK_BIN/docker" <<'MOCK_DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
printf 'docker %s\n' "$*" >>"$PREPARE_DOCKER_CALL_LOG"
[ "${1:-}" = "info" ]
MOCK_DOCKER

cat >"$MOCK_BIN/sudo" <<'MOCK_SUDO'
#!/usr/bin/env bash
set -Eeuo pipefail
printf 'sudo %s\n' "$*" >>"$PREPARE_DOCKER_CALL_LOG"
exec "$@"
MOCK_SUDO

cat >"$MOCK_BIN/flock" <<'MOCK_FLOCK'
#!/usr/bin/env bash
set -Eeuo pipefail
[ "${1:-}" = "-x" ]
shift 2
exec "$@"
MOCK_FLOCK

cat >"$TMP_ROOT/configure-mirror.sh" <<'MOCK_MIRROR'
#!/usr/bin/env bash
set -Eeuo pipefail
printf 'mirror %s\n' "${DOCKER_RESTART_WAIT_SECONDS:-}" \
  >>"$PREPARE_DOCKER_CALL_LOG"
MOCK_MIRROR

chmod +x \
  "$MOCK_BIN/docker" \
  "$MOCK_BIN/sudo" \
  "$MOCK_BIN/flock" \
  "$TMP_ROOT/configure-mirror.sh"

PATH="$MOCK_BIN:$PATH" \
PREPARE_DOCKER_CALL_LOG="$CALL_LOG" \
DOCKER_MIRROR_SCRIPT="$TMP_ROOT/configure-mirror.sh" \
DOCKER_PREPARE_LOCK="$TMP_ROOT/prepare.lock" \
DOCKER_SOCKET="$TMP_ROOT/missing.sock" \
  bash "$PREPARE_SCRIPT"

grep -Fq 'mirror 60' "$CALL_LOG"
[ "$(grep -Fc 'docker info' "$CALL_LOG")" -eq 2 ]

echo "Docker preparation wrapper simulation passed."

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
if [ "${2:-}" = "--format" ]; then
  printf '%s\n' "${PREPARE_DOCKER_MIRRORS:-}"
fi
MOCK_DOCKER

cat >"$MOCK_BIN/sudo" <<'MOCK_SUDO'
#!/usr/bin/env bash
set -Eeuo pipefail
printf 'sudo %s\n' "$*" >>"$PREPARE_DOCKER_CALL_LOG"
exec "$@"
MOCK_SUDO

chmod +x \
  "$MOCK_BIN/docker" \
  "$MOCK_BIN/sudo"

PATH="$MOCK_BIN:$PATH" \
PREPARE_DOCKER_CALL_LOG="$CALL_LOG" \
PREPARE_DOCKER_MIRRORS='https://mirror.ccs.tencentyun.com/' \
DOCKER_SOCKET="$TMP_ROOT/missing.sock" \
  bash "$PREPARE_SCRIPT"

if grep -Fq 'sudo ' "$CALL_LOG"; then
  echo "Docker preparation must not mutate or restart the host daemon." >&2
  exit 1
fi

set +e
PATH="$MOCK_BIN:$PATH" \
PREPARE_DOCKER_CALL_LOG="$CALL_LOG" \
PREPARE_DOCKER_MIRRORS='' \
DOCKER_SOCKET="$TMP_ROOT/missing.sock" \
  bash "$PREPARE_SCRIPT" >"$TMP_ROOT/missing-mirror.log" 2>&1
missing_mirror_status=$?
set -e

[ "$missing_mirror_status" -ne 0 ]
grep -Fq 'before starting the self-hosted runner' "$TMP_ROOT/missing-mirror.log"

echo "Docker preparation wrapper simulation passed."

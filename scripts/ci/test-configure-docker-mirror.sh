#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
MIRROR_SCRIPT="$ROOT_DIR/deploy/scripts/configure-docker-mirror.sh"
TMP_ROOT="$(mktemp -d)"
MOCK_BIN="$TMP_ROOT/bin"
SYSTEMCTL_LOG="$TMP_ROOT/systemctl.log"
MIRROR_URL="https://mirror.ccs.tencentyun.com"

cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

mkdir -p "$MOCK_BIN"

cat >"$MOCK_BIN/sudo" <<'MOCK_SUDO'
#!/usr/bin/env bash
set -Eeuo pipefail
exec "$@"
MOCK_SUDO

cat >"$MOCK_BIN/systemctl" <<'MOCK_SYSTEMCTL'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >>"$MOCK_SYSTEMCTL_LOG"

if [ "${MOCK_SYSTEMCTL_FAIL_ONCE:-0}" = "1" ] \
  && [ "$*" = "restart docker" ] \
  && [ ! -f "$MOCK_SYSTEMCTL_FAIL_MARKER" ]; then
  touch "$MOCK_SYSTEMCTL_FAIL_MARKER"
  exit 1
fi
MOCK_SYSTEMCTL

cat >"$MOCK_BIN/docker" <<'MOCK_DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail

case "${1:-}" in
  info)
    if [ "${2:-}" = "--format" ]; then
      python3 - "$MOCK_DAEMON_JSON" <<'PY'
import json
import os
import sys

path = sys.argv[1]
if not os.path.exists(path):
    print("[]")
else:
    with open(path, "r", encoding="utf-8") as source:
        config = json.load(source)
    print(json.dumps(config.get("registry-mirrors", [])))
PY
    else
      echo "Docker mock is ready"
    fi
    ;;
  *)
    echo "Unexpected docker invocation: $*" >&2
    exit 1
    ;;
esac
MOCK_DOCKER

chmod +x "$MOCK_BIN/sudo" "$MOCK_BIN/systemctl" "$MOCK_BIN/docker"

run_script() {
  local daemon_json=$1
  shift
  PATH="$MOCK_BIN:$PATH" \
  MOCK_DAEMON_JSON="$daemon_json" \
  MOCK_SYSTEMCTL_LOG="$SYSTEMCTL_LOG" \
  DOCKER_DAEMON_JSON="$daemon_json" \
  DOCKER_MIRROR_URL="$MIRROR_URL" \
  DOCKER_RESTART_WAIT_SECONDS=4 \
    "$@" bash "$MIRROR_SCRIPT"
}

VALID_JSON="$TMP_ROOT/valid/daemon.json"
mkdir -p "$(dirname "$VALID_JSON")"
cat >"$VALID_JSON" <<'JSON'
{
  "log-driver": "local",
  "registry-mirrors": [
    "https://existing.example"
  ]
}
JSON

run_script "$VALID_JSON" env

python3 - "$VALID_JSON" "$MIRROR_URL" <<'PY'
import json
import sys

path, mirror = sys.argv[1:]
with open(path, "r", encoding="utf-8") as source:
    config = json.load(source)
assert config["log-driver"] == "local"
assert config["registry-mirrors"] == ["https://existing.example", mirror]
PY

[ "$(grep -Fc 'restart docker' "$SYSTEMCTL_LOG")" -eq 1 ]
[ "$(find "$(dirname "$VALID_JSON")" -maxdepth 1 -name 'daemon.json.bak.*' | wc -l | tr -d ' ')" -eq 1 ]

# 再次执行必须保持幂等，不得重复重启 Docker 或生成备份。
run_script "$VALID_JSON" env
[ "$(grep -Fc 'restart docker' "$SYSTEMCTL_LOG")" -eq 1 ]
[ "$(find "$(dirname "$VALID_JSON")" -maxdepth 1 -name 'daemon.json.bak.*' | wc -l | tr -d ' ')" -eq 1 ]

# 非法 JSON 必须拒绝修改。
INVALID_JSON="$TMP_ROOT/invalid/daemon.json"
mkdir -p "$(dirname "$INVALID_JSON")"
printf '%s\n' '{invalid-json' >"$INVALID_JSON"
cp "$INVALID_JSON" "$INVALID_JSON.before"
set +e
run_script "$INVALID_JSON" env >"$TMP_ROOT/invalid.log" 2>&1
invalid_status=$?
set -e
[ "$invalid_status" -ne 0 ]
cmp -s "$INVALID_JSON" "$INVALID_JSON.before"

# Docker 重启失败时必须恢复原配置。
ROLLBACK_JSON="$TMP_ROOT/rollback/daemon.json"
mkdir -p "$(dirname "$ROLLBACK_JSON")"
printf '%s\n' '{"log-driver":"json-file"}' >"$ROLLBACK_JSON"
cp "$ROLLBACK_JSON" "$ROLLBACK_JSON.before"
set +e
run_script "$ROLLBACK_JSON" env \
  MOCK_SYSTEMCTL_FAIL_ONCE=1 \
  MOCK_SYSTEMCTL_FAIL_MARKER="$TMP_ROOT/systemctl-failed" \
  >"$TMP_ROOT/rollback.log" 2>&1
rollback_status=$?
set -e
[ "$rollback_status" -ne 0 ]
cmp -s "$ROLLBACK_JSON" "$ROLLBACK_JSON.before"
grep -Fq 'restoring the previous daemon configuration' "$TMP_ROOT/rollback.log"

echo "Tencent Cloud Docker mirror configuration simulation passed."

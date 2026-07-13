#!/usr/bin/env bash
set -Eeuo pipefail

MIRROR_URL="${DOCKER_MIRROR_URL:-https://mirror.ccs.tencentyun.com}"
DAEMON_JSON="${DOCKER_DAEMON_JSON:-/etc/docker/daemon.json}"
WAIT_SECONDS="${DOCKER_RESTART_WAIT_SECONDS:-60}"

require_command() {
  local command_name=$1
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is not available: ${command_name}" >&2
    exit 1
  fi
}

run_as_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
    return
  fi

  require_command sudo
  sudo "$@"
}

require_command cmp
require_command docker
require_command install
require_command mktemp
require_command python3
require_command systemctl

if ! [[ "$WAIT_SECONDS" =~ ^[0-9]+$ ]] || [ "$WAIT_SECONDS" -lt 1 ]; then
  echo "DOCKER_RESTART_WAIT_SECONDS must be a positive integer." >&2
  exit 1
fi

TMP_JSON="$(mktemp "${TMPDIR:-/tmp}/aegisops-daemon-json.XXXXXX")"
BACKUP_PATH=""
HAD_ORIGINAL=0
cleanup() {
  rm -f "$TMP_JSON"
}
trap cleanup EXIT

python3 - "$DAEMON_JSON" "$MIRROR_URL" "$TMP_JSON" <<'PY'
import json
import os
import sys

source_path, mirror_url, output_path = sys.argv[1:]
config = {}

if os.path.exists(source_path) and os.path.getsize(source_path) > 0:
    with open(source_path, "r", encoding="utf-8") as source:
        parsed = json.load(source)
    if not isinstance(parsed, dict):
        raise SystemExit(f"Docker daemon config must be a JSON object: {source_path}")
    config = parsed

mirrors = config.get("registry-mirrors", [])
if mirrors is None:
    mirrors = []
if not isinstance(mirrors, list) or not all(isinstance(item, str) for item in mirrors):
    raise SystemExit("registry-mirrors must be an array of strings")

if mirror_url not in mirrors:
    mirrors.append(mirror_url)
config["registry-mirrors"] = mirrors

with open(output_path, "w", encoding="utf-8") as output:
    json.dump(config, output, ensure_ascii=False, indent=2, sort_keys=True)
    output.write("\n")
PY

if [ -f "$DAEMON_JSON" ] && cmp -s "$DAEMON_JSON" "$TMP_JSON"; then
  echo "Docker mirror is already configured: ${MIRROR_URL}"
else
  DAEMON_DIR="$(dirname "$DAEMON_JSON")"
  run_as_root mkdir -p "$DAEMON_DIR"

  if [ -f "$DAEMON_JSON" ]; then
    HAD_ORIGINAL=1
    BACKUP_PATH="${DAEMON_JSON}.bak.$(date +%Y%m%d-%H%M%S)"
    run_as_root cp -a "$DAEMON_JSON" "$BACKUP_PATH"
    echo "Backed up Docker daemon config to ${BACKUP_PATH}"
  fi

  run_as_root install -m 0644 "$TMP_JSON" "$DAEMON_JSON"

  if ! run_as_root systemctl daemon-reload || ! run_as_root systemctl restart docker; then
    echo "Docker restart failed; restoring the previous daemon configuration." >&2
    if [ "$HAD_ORIGINAL" = "1" ]; then
      run_as_root cp -a "$BACKUP_PATH" "$DAEMON_JSON" || true
    else
      run_as_root rm -f "$DAEMON_JSON" || true
    fi
    run_as_root systemctl daemon-reload >/dev/null 2>&1 || true
    run_as_root systemctl restart docker >/dev/null 2>&1 || true
    exit 1
  fi
fi

for ((elapsed = 0; elapsed < WAIT_SECONDS; elapsed += 2)); do
  if docker info >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! docker info >/dev/null 2>&1; then
  echo "Docker did not become ready within ${WAIT_SECONDS}s." >&2
  exit 1
fi

MIRRORS="$(docker info --format '{{json .RegistryConfig.Mirrors}}' 2>/dev/null || true)"
if [[ "$MIRRORS" != *"$MIRROR_URL"* ]]; then
  echo "Docker is running, but the configured mirror was not reported: ${MIRROR_URL}" >&2
  echo "Reported mirrors: ${MIRRORS:-<empty>}" >&2
  exit 1
fi

echo "Docker mirror ready: ${MIRROR_URL}"

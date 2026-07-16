#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCKER_SOCKET="${DOCKER_SOCKET:-/var/run/docker.sock}"
DOCKER_MIRROR_SCRIPT="${DOCKER_MIRROR_SCRIPT:-$ROOT_DIR/deploy/scripts/configure-docker-mirror.sh}"
DOCKER_PREPARE_LOCK="${DOCKER_PREPARE_LOCK:-/tmp/aegisops-prepare-docker.lock}"
DOCKER_RESTART_WAIT_SECONDS="${DOCKER_RESTART_WAIT_SECONDS:-60}"

if ! docker info >/dev/null 2>&1; then
  if [ ! -S "$DOCKER_SOCKET" ]; then
    echo "Docker socket is not available: $DOCKER_SOCKET" >&2
    exit 1
  fi

  if [ ! -r "$DOCKER_SOCKET" ] || [ ! -w "$DOCKER_SOCKET" ]; then
    sudo chown "$(id -u):$(id -g)" "$DOCKER_SOCKET"
  fi
fi

if ! command -v flock >/dev/null 2>&1; then
  echo "Required command is not available: flock" >&2
  exit 1
fi

if [ ! -f "$DOCKER_MIRROR_SCRIPT" ]; then
  echo "Docker mirror configuration script is not available: $DOCKER_MIRROR_SCRIPT" >&2
  exit 1
fi

flock -x "$DOCKER_PREPARE_LOCK" \
  sudo env DOCKER_RESTART_WAIT_SECONDS="$DOCKER_RESTART_WAIT_SECONDS" \
  bash "$DOCKER_MIRROR_SCRIPT"

# Restarting the daemon may recreate the socket with root-only ownership.
if [ -S "$DOCKER_SOCKET" ] \
  && { [ ! -r "$DOCKER_SOCKET" ] || [ ! -w "$DOCKER_SOCKET" ]; }; then
  sudo chown "$(id -u):$(id -g)" "$DOCKER_SOCKET"
fi

docker info

#!/usr/bin/env bash
set -euo pipefail

DOCKER_SOCKET="${DOCKER_SOCKET:-/var/run/docker.sock}"
DOCKER_MIRROR_URL="${DOCKER_MIRROR_URL:-https://mirror.ccs.tencentyun.com}"

if ! docker info >/dev/null 2>&1; then
  if [ ! -S "$DOCKER_SOCKET" ]; then
    echo "Docker socket is not available: $DOCKER_SOCKET" >&2
    exit 1
  fi

  if [ ! -r "$DOCKER_SOCKET" ] || [ ! -w "$DOCKER_SOCKET" ]; then
    sudo chown "$(id -u):$(id -g)" "$DOCKER_SOCKET"
  fi
fi

MIRRORS="$(docker info --format '{{json .RegistryConfig.Mirrors}}')"
if [[ "$MIRRORS" != *"\"$DOCKER_MIRROR_URL\""* ]]; then
  echo "Docker mirror is not configured: $DOCKER_MIRROR_URL" >&2
  echo "Run 'sudo bash deploy/scripts/configure-docker-mirror.sh' on the host before starting the self-hosted runner." >&2
  exit 1
fi

docker info

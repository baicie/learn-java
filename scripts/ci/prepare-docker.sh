#!/usr/bin/env bash
set -euo pipefail

DOCKER_SOCKET="${DOCKER_SOCKET:-/var/run/docker.sock}"

if ! docker info >/dev/null 2>&1; then
  if [ ! -S "$DOCKER_SOCKET" ]; then
    echo "Docker socket is not available: $DOCKER_SOCKET" >&2
    exit 1
  fi

  if [ ! -r "$DOCKER_SOCKET" ] || [ ! -w "$DOCKER_SOCKET" ]; then
    sudo chown "$(id -u):$(id -g)" "$DOCKER_SOCKET"
  fi
fi

docker info

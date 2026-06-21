#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAR="${1:-images/aegisops-images.tar}"
TARGET_REGISTRY="${2:-}"

if [[ ! -f "$IMAGE_TAR" ]]; then
  echo "Image tar not found: $IMAGE_TAR" >&2
  exit 1
fi

docker load -i "$IMAGE_TAR"

if [[ -n "$TARGET_REGISTRY" ]]; then
  VERSION="${VERSION:-0.1.0}"

  for image in aiops-server aiops-worker aiops-runner aiops-agent; do
    docker tag "aegisops/${image}:${VERSION}" "${TARGET_REGISTRY}/${image}:${VERSION}"
    docker push "${TARGET_REGISTRY}/${image}:${VERSION}"
  done
fi

echo "Offline images loaded."

#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:-0.1.0}"
REGISTRY="${REGISTRY:-aegisops}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

docker build \
  -f deploy/docker/java-app.Dockerfile \
  --build-arg APP_MODULE=apps/aiops-server \
  --build-arg APP_NAME=aiops-server \
  --build-arg APP_PORT=8080 \
  -t "${REGISTRY}/aiops-server:${VERSION}" .

docker build \
  -f deploy/docker/java-app.Dockerfile \
  --build-arg APP_MODULE=apps/aiops-worker \
  --build-arg APP_NAME=aiops-worker \
  --build-arg APP_PORT=8081 \
  -t "${REGISTRY}/aiops-worker:${VERSION}" .

docker build \
  -f deploy/docker/java-app.Dockerfile \
  --build-arg APP_MODULE=apps/aiops-runner \
  --build-arg APP_NAME=aiops-runner \
  --build-arg APP_PORT=8092 \
  -t "${REGISTRY}/aiops-runner:${VERSION}" .

docker build \
  -f deploy/docker/aiops-agent.Dockerfile \
  -t "${REGISTRY}/aiops-agent:${VERSION}" .

echo "Built AegisOps images with version=${VERSION}, registry=${REGISTRY}"

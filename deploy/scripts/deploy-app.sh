#!/usr/bin/env bash
set -Eeuo pipefail

: "${IMAGE_PREFIX:?IMAGE_PREFIX is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"

APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.app.yml}"
RUNTIME_DIR="${RUNTIME_DIR:-deploy/.runtime}"
OVERRIDE_FILE="$RUNTIME_DIR/images.yml"
PREVIOUS_OVERRIDE_FILE="$RUNTIME_DIR/images.previous.yml"
NEXT_OVERRIDE_FILE="$RUNTIME_DIR/images.next.yml"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-240}"

cd "$APP_DIR"
mkdir -p "$RUNTIME_DIR"

compose() {
  docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE_FILE" "$@"
}

pull_with_retry() {
  local image="$1"
  local attempt

  for attempt in 1 2 3 4 5; do
    echo "Pulling $image (attempt $attempt/5)"
    if docker pull "$image"; then
      return 0
    fi
    sleep $((attempt * 10))
  done

  echo "Failed to pull image after retries: $image" >&2
  return 1
}

write_override() {
  local target="$1"
  cat >"$target" <<EOF
services:
  aiops-server:
    image: ${IMAGE_PREFIX}:${IMAGE_TAG}
  aiops-agent:
    image: ${IMAGE_PREFIX}/aiops-agent:${IMAGE_TAG}
  aiops-worker:
    image: ${IMAGE_PREFIX}/aiops-worker:${IMAGE_TAG}
  aiops-runner:
    image: ${IMAGE_PREFIX}/aiops-runner:${IMAGE_TAG}
EOF
}

wait_for_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  local containers=(
    aegisops-postgres
    aegisops-agent
    aegisops-server
    aegisops-worker
    aegisops-runner
  )

  while ((SECONDS < deadline)); do
    local all_healthy=true
    local container status

    for container in "${containers[@]}"; do
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
      case "$status" in
        healthy|running)
          ;;
        exited|dead|unhealthy)
          echo "$container entered terminal state: $status" >&2
          return 1
          ;;
        *)
          all_healthy=false
          ;;
      esac
    done

    if [[ "$all_healthy" == true ]]; then
      return 0
    fi

    sleep 5
  done

  echo "Services did not become healthy within ${HEALTH_TIMEOUT_SECONDS}s" >&2
  return 1
}

show_diagnostics() {
  docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE_FILE" ps || true
  docker compose -f "$COMPOSE_FILE" -f "$OVERRIDE_FILE" logs --tail=200 aiops-server aiops-agent aiops-worker aiops-runner || true
}

rollback() {
  if [[ ! -f "$PREVIOUS_OVERRIDE_FILE" ]]; then
    echo "No previous deployment metadata exists; automatic rollback is unavailable." >&2
    return 1
  fi

  echo "Rolling back to previous image set"
  cp "$PREVIOUS_OVERRIDE_FILE" "$OVERRIDE_FILE"
  compose config --quiet
  compose pull
  compose up -d --remove-orphans
  wait_for_healthy
}

images=(
  "${IMAGE_PREFIX}:${IMAGE_TAG}"
  "${IMAGE_PREFIX}/aiops-agent:${IMAGE_TAG}"
  "${IMAGE_PREFIX}/aiops-worker:${IMAGE_TAG}"
  "${IMAGE_PREFIX}/aiops-runner:${IMAGE_TAG}"
)

for image in "${images[@]}"; do
  pull_with_retry "$image"
done

write_override "$NEXT_OVERRIDE_FILE"

docker compose -f "$COMPOSE_FILE" -f "$NEXT_OVERRIDE_FILE" config --quiet

if [[ -f "$OVERRIDE_FILE" ]]; then
  cp "$OVERRIDE_FILE" "$PREVIOUS_OVERRIDE_FILE"
fi
mv "$NEXT_OVERRIDE_FILE" "$OVERRIDE_FILE"

if ! compose up -d --remove-orphans; then
  show_diagnostics
  rollback
  exit 1
fi

if ! wait_for_healthy; then
  show_diagnostics
  rollback
  exit 1
fi

compose ps
docker image prune -f >/dev/null 2>&1 || true

echo "Deployment succeeded: ${IMAGE_PREFIX}:${IMAGE_TAG}"

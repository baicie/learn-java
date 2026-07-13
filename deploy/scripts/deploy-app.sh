#!/usr/bin/env bash
set -Eeuo pipefail

: "${IMAGE_PREFIX:?IMAGE_PREFIX is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"

APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/deploy/docker-compose.app.yml}"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "Compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

export AIOPS_SERVER_IMAGE="${IMAGE_PREFIX}:${IMAGE_TAG}"
export AIOPS_AGENT_IMAGE="${IMAGE_PREFIX}/aiops-agent:${IMAGE_TAG}"
export AIOPS_WORKER_IMAGE="${IMAGE_PREFIX}/aiops-worker:${IMAGE_TAG}"
export AIOPS_RUNNER_IMAGE="${IMAGE_PREFIX}/aiops-runner:${IMAGE_TAG}"

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

container_image() {
  docker inspect --format '{{.Config.Image}}' "$1" 2>/dev/null || true
}

PREVIOUS_SERVER_IMAGE="$(container_image aegisops-server)"
PREVIOUS_AGENT_IMAGE="$(container_image aegisops-agent)"
PREVIOUS_WORKER_IMAGE="$(container_image aegisops-worker)"
PREVIOUS_RUNNER_IMAGE="$(container_image aegisops-runner)"

ROLLBACK_READY=0
if [ -n "$PREVIOUS_SERVER_IMAGE" ] \
  && [ -n "$PREVIOUS_AGENT_IMAGE" ] \
  && [ -n "$PREVIOUS_WORKER_IMAGE" ] \
  && [ -n "$PREVIOUS_RUNNER_IMAGE" ]; then
  ROLLBACK_READY=1
fi

DEPLOYMENT_STARTED=0

print_diagnostics() {
  echo "==> Deployment diagnostics"
  compose ps || true
  compose logs --no-color --tail=120 aiops-server aiops-agent aiops-worker aiops-runner || true
}

rollback() {
  local exit_code=$?
  trap - ERR

  echo "::error::Deployment failed for image tag ${IMAGE_TAG}"
  print_diagnostics

  if [ "$DEPLOYMENT_STARTED" = "1" ] && [ "$ROLLBACK_READY" = "1" ]; then
    echo "==> Rolling back to the previously running images"
    export AIOPS_SERVER_IMAGE="$PREVIOUS_SERVER_IMAGE"
    export AIOPS_AGENT_IMAGE="$PREVIOUS_AGENT_IMAGE"
    export AIOPS_WORKER_IMAGE="$PREVIOUS_WORKER_IMAGE"
    export AIOPS_RUNNER_IMAGE="$PREVIOUS_RUNNER_IMAGE"
    compose up -d --remove-orphans --no-build || true
    compose ps || true
  else
    echo "::warning::Rollback skipped because a complete previous release was not found"
  fi

  exit "$exit_code"
}
trap rollback ERR

retry() {
  local max_attempts=$1
  shift
  local attempt=1

  until "$@"; do
    if [ "$attempt" -ge "$max_attempts" ]; then
      return 1
    fi
    echo "Attempt ${attempt}/${max_attempts} failed; retrying in $((attempt * 10))s..."
    sleep $((attempt * 10))
    attempt=$((attempt + 1))
  done
}

wait_http() {
  local name=$1
  local url=$2
  local timeout_seconds=$3
  local elapsed=0

  while [ "$elapsed" -lt "$timeout_seconds" ]; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "${name} is healthy after ${elapsed}s"
      return 0
    fi

    if [ "$(docker inspect --format '{{.State.Running}}' "$name" 2>/dev/null || true)" = "false" ]; then
      echo "${name} exited before becoming healthy" >&2
      return 1
    fi

    sleep 2
    elapsed=$((elapsed + 2))
  done

  echo "${name} did not become healthy within ${timeout_seconds}s" >&2
  return 1
}

wait_container_health() {
  local name=$1
  local timeout_seconds=$2
  local elapsed=0
  local status

  while [ "$elapsed" -lt "$timeout_seconds" ]; do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || true)"
    case "$status" in
      healthy|running)
        echo "${name} status=${status} after ${elapsed}s"
        return 0
        ;;
      unhealthy|exited|dead)
        echo "${name} status=${status}" >&2
        return 1
        ;;
    esac
    sleep 2
    elapsed=$((elapsed + 2))
  done

  echo "${name} did not become healthy within ${timeout_seconds}s" >&2
  return 1
}

echo "==> Docker versions"
docker version --format 'Docker {{.Server.Version}}'
docker compose version

echo "==> Validating deployment descriptor"
compose config --quiet

echo "==> Pulling immutable release images (${IMAGE_TAG})"
retry 5 compose pull aiops-server aiops-agent aiops-worker aiops-runner

echo "==> Applying release without stopping PostgreSQL or healthy unchanged containers"
DEPLOYMENT_STARTED=1
compose up -d --remove-orphans --no-build

echo "==> Waiting for services"
wait_container_health aegisops-postgres 120
wait_http aegisops-agent http://127.0.0.1:9008/health 120
wait_http aegisops-server http://127.0.0.1:8080/actuator/health 240
wait_http aegisops-worker http://127.0.0.1:8081/actuator/health 180
wait_http aegisops-runner http://127.0.0.1:8092/actuator/health 180

echo "==> Deployment succeeded"
compose ps

docker image prune -f --filter 'until=168h' >/dev/null 2>&1 || true

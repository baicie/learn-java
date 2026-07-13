#!/usr/bin/env bash
set -Eeuo pipefail

: "${IMAGE_PREFIX:?IMAGE_PREFIX is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"

APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/deploy/docker-compose.app.yml}"
APP_SERVICES=(aiops-server aiops-agent aiops-worker aiops-runner)
APP_CONTAINERS=(aegisops-server aegisops-agent aegisops-worker aegisops-runner)

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "Compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

require_command() {
  local command_name=$1
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is not available: ${command_name}" >&2
    exit 1
  fi
}

require_command bash
require_command docker
require_command mktemp

# 单个 Docker Hub 仓库承载四个服务，服务名编码在不可变标签中。
export AIOPS_SERVER_IMAGE="${IMAGE_PREFIX}:${IMAGE_TAG}-server"
export AIOPS_AGENT_IMAGE="${IMAGE_PREFIX}:${IMAGE_TAG}-agent"
export AIOPS_WORKER_IMAGE="${IMAGE_PREFIX}:${IMAGE_TAG}-worker"
export AIOPS_RUNNER_IMAGE="${IMAGE_PREFIX}:${IMAGE_TAG}-runner"

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

container_exists() {
  docker inspect "$1" >/dev/null 2>&1
}

container_image() {
  docker inspect --format '{{.Config.Image}}' "$1" 2>/dev/null || true
}

container_name_by_id() {
  docker inspect --format '{{.Name}}' "$1" 2>/dev/null | sed 's#^/##'
}

container_image_by_id() {
  docker inspect --format '{{.Config.Image}}' "$1" 2>/dev/null || true
}

is_managed_aegisops_container() {
  local container_id=$1
  local container_name container_image_value

  container_name="$(container_name_by_id "$container_id")"
  container_image_value="$(container_image_by_id "$container_id")"

  [[ "$container_name" == aegisops-* ]] \
    || [[ "$container_image_value" == "${IMAGE_PREFIX}:"* ]]
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
DEPLOY_STAGE="initialization"
DOCKER_CONFIG_DIR=""

cleanup_registry_auth() {
  if [ -z "$DOCKER_CONFIG_DIR" ] || [ ! -d "$DOCKER_CONFIG_DIR" ]; then
    return
  fi

  DOCKER_CONFIG="$DOCKER_CONFIG_DIR" docker logout >/dev/null 2>&1 || true
  rm -f "$DOCKER_CONFIG_DIR/config.json"
  rmdir "$DOCKER_CONFIG_DIR" 2>/dev/null || true
}
trap cleanup_registry_auth EXIT

print_port_diagnostics() {
  local port=$1
  echo "==> Port ${port} diagnostics"
  docker ps --filter "publish=${port}" \
    --format 'container={{.ID}} name={{.Names}} image={{.Image}} ports={{.Ports}}' || true

  if command -v ss >/dev/null 2>&1; then
    ss -ltnp 2>/dev/null | awk -v suffix=":${port}" '$4 ~ (suffix "$" ) { print }' || true
  elif command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true
  fi
}

print_diagnostics() {
  echo "==> Deployment diagnostics (stage=${DEPLOY_STAGE})"
  compose ps || true
  compose logs --no-color --tail=120 "${APP_SERVICES[@]}" || true
  for port in 5432 8080 8081 8092 9008; do
    print_port_diagnostics "$port"
  done
}

remove_application_containers() {
  local container_name

  echo "==> Recreating stateless application containers on the current Compose network"
  for container_name in "${APP_CONTAINERS[@]}"; do
    if ! container_exists "$container_name"; then
      continue
    fi

    if ! is_managed_aegisops_container "$container_name"; then
      echo "Container ${container_name} is not recognized as AegisOps managed; refusing to remove it." >&2
      return 1
    fi

    echo "Removing existing application container ${container_name}."
    docker rm -f "$container_name"
  done
}

rollback() {
  local exit_code=$?
  trap - ERR
  set +e

  echo "::error::Deployment failed at stage=${DEPLOY_STAGE} for image tag ${IMAGE_TAG}"
  print_diagnostics

  if [ "$DEPLOYMENT_STARTED" = "1" ] && [ "$ROLLBACK_READY" = "1" ]; then
    DEPLOY_STAGE="rollback"
    echo "==> Rolling back to the previously running images"
    export AIOPS_SERVER_IMAGE="$PREVIOUS_SERVER_IMAGE"
    export AIOPS_AGENT_IMAGE="$PREVIOUS_AGENT_IMAGE"
    export AIOPS_WORKER_IMAGE="$PREVIOUS_WORKER_IMAGE"
    export AIOPS_RUNNER_IMAGE="$PREVIOUS_RUNNER_IMAGE"

    # 先删除失败版本的无状态应用容器，避免旧 endpoint 继续占用宿主机端口，
    # PostgreSQL 容器与数据卷始终保留。
    remove_application_containers
    compose up -d --remove-orphans --no-build
    compose ps
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

pull_image() {
  local image=$1
  echo "==> Pulling ${image}"
  retry 5 docker pull "$image"
}

port_is_listening() {
  local port=$1

  if command -v ss >/dev/null 2>&1; then
    ss -H -ltn 2>/dev/null \
      | awk -v suffix=":${port}" '$4 ~ (suffix "$" ) { found=1 } END { exit found ? 0 : 1 }'
    return
  fi

  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
    return
  fi

  # 无宿主机端口检查工具时只依赖 Docker publish 检查。
  return 1
}

wait_port_free() {
  local port=$1
  local attempt

  for attempt in 1 2 3 4 5; do
    if ! port_is_listening "$port"; then
      return 0
    fi
    sleep 1
  done

  return 1
}

ensure_port_available() {
  local port=$1
  local expected_container=$2
  local auto_reclaim=$3
  local owner_id owner_name
  local expected_owner_found=0
  local foreign_owner_found=0
  local stale_owner_removed=0

  while IFS= read -r owner_id; do
    [ -n "$owner_id" ] || continue
    owner_name="$(container_name_by_id "$owner_id")"

    if [ "$owner_name" = "$expected_container" ]; then
      expected_owner_found=1
      echo "Port ${port} is currently owned by expected container ${expected_container}; it will be recreated safely."
      continue
    fi

    if [ "$auto_reclaim" = "true" ] && is_managed_aegisops_container "$owner_id"; then
      echo "Removing stale AegisOps container ${owner_name:-$owner_id} that occupies port ${port}."
      docker rm -f "$owner_id"
      stale_owner_removed=1
      continue
    fi

    foreign_owner_found=1
    echo "Port ${port} is occupied by unmanaged container ${owner_name:-$owner_id}; refusing to stop it automatically." >&2
  done < <(docker ps --filter "publish=${port}" --format '{{.ID}}')

  if [ "$foreign_owner_found" = "1" ]; then
    print_port_diagnostics "$port"
    return 1
  fi

  if [ "$expected_owner_found" = "1" ]; then
    return 0
  fi

  if [ "$stale_owner_removed" = "1" ] && ! wait_port_free "$port"; then
    echo "Port ${port} is still occupied after removing stale AegisOps containers." >&2
    print_port_diagnostics "$port"
    return 1
  fi

  if port_is_listening "$port"; then
    echo "Port ${port} is occupied by a host process or an undetected runtime; refusing to terminate it automatically." >&2
    print_port_diagnostics "$port"
    return 1
  fi

  echo "Port ${port} is available."
}

validate_host_ports() {
  # PostgreSQL 数据端口不做自动接管，避免误停其它数据库实例。
  ensure_port_available 5432 aegisops-postgres false
  ensure_port_available 8080 aegisops-server true
  ensure_port_available 8081 aegisops-worker true
  ensure_port_available 8092 aegisops-runner true
  ensure_port_available 9008 aegisops-agent true
}

wait_container_health() {
  local name=$1
  local timeout_seconds=$2
  local elapsed=0
  local status

  while [ "$elapsed" -lt "$timeout_seconds" ]; do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || true)"
    case "$status" in
      healthy)
        echo "${name} status=healthy after ${elapsed}s"
        return 0
        ;;
      unhealthy|exited|dead)
        echo "${name} status=${status}" >&2
        return 1
        ;;
      running)
        # 没有声明 HEALTHCHECK 的容器只能退化为 running；生产 Compose 中五个容器均应有健康检查。
        echo "${name} has no health status; accepting running state after ${elapsed}s"
        return 0
        ;;
    esac
    sleep 2
    elapsed=$((elapsed + 2))
  done

  echo "${name} did not become healthy within ${timeout_seconds}s (lastStatus=${status:-missing})" >&2
  return 1
}

DEPLOY_STAGE="docker-preflight"
echo "==> Docker versions"
docker version --format 'Docker {{.Server.Version}}'
docker compose version
docker info >/dev/null

if [ -n "${DOCKERHUB_USERNAME:-}" ] || [ -n "${DOCKERHUB_TOKEN:-}" ]; then
  : "${DOCKERHUB_USERNAME:?DOCKERHUB_USERNAME and DOCKERHUB_TOKEN must be provided together}"
  : "${DOCKERHUB_TOKEN:?DOCKERHUB_USERNAME and DOCKERHUB_TOKEN must be provided together}"

  DEPLOY_STAGE="registry-login"
  DOCKER_CONFIG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aegisops-docker-config.XXXXXX")"
  chmod 700 "$DOCKER_CONFIG_DIR"
  export DOCKER_CONFIG="$DOCKER_CONFIG_DIR"
  printf '%s' "$DOCKERHUB_TOKEN" \
    | docker login --username "$DOCKERHUB_USERNAME" --password-stdin
else
  echo "==> Registry login skipped; using existing Docker credentials or public images"
fi

DEPLOY_STAGE="compose-validation"
echo "==> Validating deployment descriptor"
compose config --quiet

DEPLOY_STAGE="image-pull"
echo "==> Pulling immutable release images (${IMAGE_TAG})"
pull_image "$AIOPS_SERVER_IMAGE"
pull_image "$AIOPS_AGENT_IMAGE"
pull_image "$AIOPS_WORKER_IMAGE"
pull_image "$AIOPS_RUNNER_IMAGE"

DEPLOY_STAGE="port-preflight"
echo "==> Validating required host ports"
validate_host_ports

DEPLOY_STAGE="application-recreate"
DEPLOYMENT_STARTED=1
remove_application_containers

DEPLOY_STAGE="compose-up"
echo "==> Starting release while preserving PostgreSQL and its data volume"
compose up -d --remove-orphans --no-build

DEPLOY_STAGE="health-check"
echo "==> Waiting for container health"
wait_container_health aegisops-postgres 120
wait_container_health aegisops-agent 120
wait_container_health aegisops-server 240
wait_container_health aegisops-worker 180
wait_container_health aegisops-runner 180

DEPLOY_STAGE="complete"
echo "==> Deployment succeeded"
compose ps

docker image prune -f --filter 'until=168h' >/dev/null 2>&1 || true

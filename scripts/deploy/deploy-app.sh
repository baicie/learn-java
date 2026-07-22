#!/usr/bin/env bash
#
# 在本机或 VM 用 docker compose 一键拉起 AegisOps（postgres + server + agent + worker + runner）。
# 镜像名约定：aegisops/<app>:<tag>，与 scripts/deploy/build-images.sh 一致。
#
# 用法：
#   VERSION=0.1.0 REGISTRY=aegisops ./scripts/deploy/deploy-app.sh up
#   ./scripts/deploy/deploy-app.sh down
#   ./scripts/deploy/deploy-app.sh logs server
#   ./scripts/deploy/deploy-app.sh status
#
# 与 GitHub Release 工作流的差异：
#   - 本脚本面向"本地或单 VM 一键起"；复制源 compose 到临时目录后再 sed 占位符，避免污染源文件
#   - Release 工作流通过 deploy/scripts/deploy-app.sh 在腾讯云 VM 部署
#
# 注意：
#   - 不会修改 deploy/docker-compose.app.yml
#   - 镜像需先存在；缺失时直接报错给 build-images.sh 提示

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

VERSION="${VERSION:-0.1.0}"
REGISTRY="${REGISTRY:-aegisops}"
SOURCE_COMPOSE="${SOURCE_COMPOSE:-$ROOT_DIR/deploy/docker-compose.app.yml}"
ACTION="${1:-up}"
PROJECT_NAME="${PROJECT_NAME:-aegisops-app}"

if [ ! -f "$SOURCE_COMPOSE" ]; then
  echo "::error::source compose not found: $SOURCE_COMPOSE" >&2
  exit 1
fi

# 临时 compose：复制 + sed 占位符；脚本退出时清理
TMP_DIR="$(mktemp -d -t aegisops-deploy.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT
RUNTIME_COMPOSE="$TMP_DIR/docker-compose.yml"
cp "$SOURCE_COMPOSE" "$RUNTIME_COMPOSE"

sed_in_place() {
  if sed --version >/dev/null 2>&1; then
    sed -i "$@"
  else
    sed -i '' "$@"
  fi
}

stamp_compose() {
  # 占位符由生产 Compose 文件保留：
  #   server  : DOCKERHUB_USERNAME_REPLACE_ME/aegisops:git-SHA_REPLACE_ME
  #   其他    : DOCKERHUB_USERNAME_REPLACE_ME/aegisops/aiops-<app>:git-SHA_REPLACE_ME
  sed_in_place "s|DOCKERHUB_USERNAME_REPLACE_ME/aegisops:git-SHA_REPLACE_ME|${REGISTRY}/aiops-server:${VERSION}|" "$RUNTIME_COMPOSE"
  sed_in_place "s|DOCKERHUB_USERNAME_REPLACE_ME/aegisops/aiops-agent:git-SHA_REPLACE_ME|${REGISTRY}/aiops-agent:${VERSION}|" "$RUNTIME_COMPOSE"
  sed_in_place "s|DOCKERHUB_USERNAME_REPLACE_ME/aegisops/aiops-worker:git-SHA_REPLACE_ME|${REGISTRY}/aiops-worker:${VERSION}|" "$RUNTIME_COMPOSE"
  sed_in_place "s|DOCKERHUB_USERNAME_REPLACE_ME/aegisops/aiops-runner:git-SHA_REPLACE_ME|${REGISTRY}/aiops-runner:${VERSION}|" "$RUNTIME_COMPOSE"
}

stamp_compose

ensure_image() {
  local image=$1
  if docker image inspect "$image" >/dev/null 2>&1; then
    echo "image present: $image"
    return 0
  fi
  echo "image missing: $image"
  echo "::error::请先跑：VERSION=$VERSION REGISTRY=$REGISTRY ./scripts/deploy/build-images.sh" >&2
  return 1
}

require_images() {
  local missing=0
  for img in \
    "${REGISTRY}/aiops-server:${VERSION}" \
    "${REGISTRY}/aiops-agent:${VERSION}" \
    "${REGISTRY}/aiops-worker:${VERSION}" \
    "${REGISTRY}/aiops-runner:${VERSION}"
  do
    ensure_image "$img" || missing=1
  done
  return $missing
}

cmd_up() {
  echo "::group::compose preview"
  grep '^[[:space:]]*image:' "$RUNTIME_COMPOSE" || true
  echo "::endgroup::"

  if ! require_images; then
    exit 2
  fi

  docker compose -p "$PROJECT_NAME" -f "$RUNTIME_COMPOSE" pull --ignore-pull-failures || true
  docker compose -p "$PROJECT_NAME" -f "$RUNTIME_COMPOSE" up -d

  echo "::group::waiting healthchecks"
  check() {
    local name=$1 url=$2 max=${3:-30}
    for i in $(seq 1 "$max"); do
      if curl -fsS "$url" >/dev/null 2>&1; then
        echo "$name healthy after ${i}s"
        return 0
      fi
      sleep 2
    done
    echo "::warning::$name did not become healthy in $((max*2))s"
    return 1
  }
  check server http://127.0.0.1:8080/actuator/health 30
  check agent  http://127.0.0.1:9008/health             15
  check worker http://127.0.0.1:8081/actuator/health  30
  check runner http://127.0.0.1:8092/actuator/health  30
  echo "::endgroup::"
}

cmd_down() {
  docker compose -p "$PROJECT_NAME" -f "$RUNTIME_COMPOSE" down
}

cmd_status() {
  docker compose -p "$PROJECT_NAME" -f "$RUNTIME_COMPOSE" ps
  echo "---"
  for u in \
    "server http://127.0.0.1:8080/actuator/health" \
    "agent  http://127.0.0.1:9008/health" \
    "worker http://127.0.0.1:8081/actuator/health" \
    "runner http://127.0.0.1:8092/actuator/health"
  do
    name=$(echo "$u" | awk '{print $1}')
    url=$(echo "$u" | awk '{print $2}')
    if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then
      echo "OK    $name  $url"
    else
      echo "DOWN  $name  $url"
    fi
  done
}

cmd_logs() {
  local target=${1:-}
  if [ -z "$target" ]; then
    docker compose -p "$PROJECT_NAME" -f "$RUNTIME_COMPOSE" logs --tail=200
  else
    docker compose -p "$PROJECT_NAME" -f "$RUNTIME_COMPOSE" logs --tail=200 "$target"
  fi
}

case "$ACTION" in
  up)     cmd_up ;;
  down)   cmd_down ;;
  status) cmd_status ;;
  logs)   shift; cmd_logs "${1:-}" ;;
  *)
    echo "usage: $0 {up|down|status|logs [service]}" >&2
    exit 64
    ;;
esac

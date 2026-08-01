#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
INSTALL_SCRIPT="$ROOT_DIR/deploy/install.sh"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/deploy/docker-compose.core.yml}"
RUNTIME_DIR="${AIOPS_RUNTIME_DIR:-$ROOT_DIR/deploy/runtime}"
ENV_FILE="$RUNTIME_DIR/.env"

VERSION="${VERSION:-0.1.0}"
REGISTRY="${REGISTRY:-aegisops}"
ACTION="${1:-up}"
DEPLOY_MODE="${AIOPS_DEPLOY_MODE:-diagnostic}"

case "$DEPLOY_MODE" in
  core|diagnostic|automation) ;;
  *)
    echo "Unsupported AIOPS_DEPLOY_MODE: $DEPLOY_MODE" >&2
    exit 2
    ;;
esac

compose_profiles=()
case "$DEPLOY_MODE" in
  diagnostic)
    compose_profiles+=(--profile ai)
    ;;
  automation)
    compose_profiles+=(--profile ai --profile automation)
    ;;
esac

export AIOPS_APP_IMAGE="${AIOPS_APP_IMAGE:-${REGISTRY}/aegisops-app:${VERSION}}"
export AIOPS_AGENT_IMAGE="${AIOPS_AGENT_IMAGE:-${REGISTRY}/aiops-agent:${VERSION}}"
export AIOPS_RUNNER_IMAGE="${AIOPS_RUNNER_IMAGE:-${REGISTRY}/aiops-runner:${VERSION}}"

compose() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    "${compose_profiles[@]}" \
    "$@"
}

require_runtime() {
  if [ ! -f "$ENV_FILE" ] || [ ! -d "$RUNTIME_DIR/secrets" ]; then
    echo "Deployment runtime is missing; run '$0 up' first." >&2
    exit 1
  fi
}

case "$ACTION" in
  up)
    exec bash "$INSTALL_SCRIPT" --mode "$DEPLOY_MODE" --runtime-dir "$RUNTIME_DIR"
    ;;
  down)
    require_runtime
    compose down
    ;;
  status)
    require_runtime
    compose ps
    ;;
  logs)
    require_runtime
    shift || true
    compose logs --tail=200 "${1:-}"
    ;;
  *)
    echo "usage: $0 {up|down|status|logs [service]}" >&2
    exit 64
    ;;
esac

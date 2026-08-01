#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.core.yml"
PROJECT_NAME="${AIOPS_CORE_SMOKE_PROJECT:-aegisops-core-smoke-$$}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for the Core deployment smoke test." >&2
  exit 1
fi

cleanup() {
  docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

cd "$ROOT_DIR"

export AIOPS_SERVER_PORT="${AIOPS_CORE_SMOKE_SERVER_PORT:-18080}"
export AIOPS_POSTGRES_PORT="${AIOPS_CORE_SMOKE_POSTGRES_PORT:-15432}"
export AIOPS_AGENT_ENABLED=false
export AIOPS_INTERNAL_AGENT_API_ENABLED=false
export AIOPS_OBJECT_STORAGE_ENABLED=false
export AIOPS_EVIDENCE_VICTORIA_ENABLED=false
export AIOPS_QUOTA_BACKEND=memory
export AIOPS_REDIS_HEALTH_ENABLED=false
export COMPOSE_PROGRESS=plain

docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" up --build --detach --wait

running_services="$(
  docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" ps --services --status running \
    | sort \
    | tr '\n' ' '
)"
expected_services="aiops-server aiops-worker postgres "

if [[ "$running_services" != "$expected_services" ]]; then
  echo "Unexpected Core services: $running_services" >&2
  exit 1
fi

curl --fail --silent --show-error \
  "http://127.0.0.1:${AIOPS_SERVER_PORT}/actuator/health" >/dev/null
curl --fail --silent --show-error \
  "http://127.0.0.1:${AIOPS_SERVER_PORT}/" >/dev/null

internal_agent_status="$(
  curl --silent --output /dev/null --write-out '%{http_code}' --request POST \
    "http://127.0.0.1:${AIOPS_SERVER_PORT}/internal/agent/auth/probe"
)"
if [[ "$internal_agent_status" != "404" ]]; then
  echo "Internal Agent API must be absent in Core, got HTTP $internal_agent_status." >&2
  exit 1
fi

echo "Core deployment smoke passed: Portal, Server, Worker, and PostgreSQL are healthy; Agent API is absent."

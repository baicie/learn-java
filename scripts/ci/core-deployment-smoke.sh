#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.core.yml"
PROJECT_NAME="${AIOPS_CORE_SMOKE_PROJECT:-aegisops-core-smoke-$$}"
runtime_dir=""

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for the Core deployment smoke test." >&2
  exit 1
fi

cleanup() {
  if [ -n "$runtime_dir" ] && [ -f "$runtime_dir/.env" ]; then
    docker compose --env-file "$runtime_dir/.env" --profile ai \
      -p "$PROJECT_NAME" -f "$COMPOSE_FILE" \
      down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi
  if [ -n "$runtime_dir" ]; then
    rm -rf -- "$runtime_dir"
  fi
}
trap cleanup EXIT

cd "$ROOT_DIR"

export AIOPS_APP_PORT="${AIOPS_CORE_SMOKE_APP_PORT:-18080}"
export AIOPS_OBJECT_STORAGE_ENABLED=false
export AIOPS_EVIDENCE_VICTORIA_ENABLED=false
export AIOPS_QUOTA_BACKEND=memory
export AIOPS_REDIS_HEALTH_ENABLED=false
export COMPOSE_PROGRESS=plain
export COMPOSE_PROJECT_NAME="$PROJECT_NAME"

runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/aegisops-core-smoke.XXXXXX")"
export AIOPS_SECRETS_DIR="$runtime_dir/secrets"
bash deploy/install.sh --mode diagnostic --runtime-dir "$runtime_dir"

running_services="$(
  docker compose --env-file "$runtime_dir/.env" --profile ai \
    -p "$PROJECT_NAME" -f "$COMPOSE_FILE" ps --services --status running \
    | sort \
    | tr '\n' ' '
)"
expected_services="aegisops-app aiops-agent postgres "

if [[ "$running_services" != "$expected_services" ]]; then
  echo "Unexpected Core services: $running_services" >&2
  exit 1
fi

curl --fail --silent --show-error \
  "http://127.0.0.1:${AIOPS_APP_PORT}/actuator/health" >/dev/null
curl --fail --silent --show-error \
  "http://127.0.0.1:${AIOPS_APP_PORT}/" >/dev/null

internal_agent_status="$(
  curl --silent --output /dev/null --write-out '%{http_code}' --request POST \
    "http://127.0.0.1:${AIOPS_APP_PORT}/internal/agent/auth/probe"
)"
if [[ "$internal_agent_status" != "404" && "$internal_agent_status" != "401" ]]; then
  echo "Internal Agent API must not be usable on the public connector, got HTTP $internal_agent_status." >&2
  exit 1
fi

echo "Diagnostic deployment smoke passed: App, Agent, and PostgreSQL are healthy."

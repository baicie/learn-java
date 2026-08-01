#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.app.yml}"
COMPOSE_OVERLAY_FILE="${COMPOSE_OVERLAY_FILE:-}"
OUTPUT_DIR="${OUTPUT_DIR:-runtime-diagnostics}"
RUNTIME_REDACTOR_SCRIPT="${RUNTIME_REDACTOR_SCRIPT:-$SCRIPT_DIR/../../deploy/scripts/redact-runtime-output.py}"

if [ ! -f "$RUNTIME_REDACTOR_SCRIPT" ]; then
  echo "Runtime output redactor not found: $RUNTIME_REDACTOR_SCRIPT" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR/inspect" "$OUTPUT_DIR/network"
rm -f "$OUTPUT_DIR/compose-config.yml"

COMPOSE_ARGS=(-f "$COMPOSE_FILE")
if [ -n "$COMPOSE_OVERLAY_FILE" ]; then
  COMPOSE_ARGS+=(-f "$COMPOSE_OVERLAY_FILE")
fi

compose() {
  docker compose "${COMPOSE_ARGS[@]}" "$@"
}

redact() {
  python3 "$RUNTIME_REDACTOR_SCRIPT"
}

capture() {
  local destination="$1"
  shift
  "$@" 2>&1 | redact >"$destination" || true
}

capture "$OUTPUT_DIR/compose-ps.txt" compose ps -a
capture "$OUTPUT_DIR/compose-services.txt" compose config --services
capture "$OUTPUT_DIR/compose-images.txt" compose config --images
capture "$OUTPUT_DIR/compose.log" compose logs --no-color
capture "$OUTPUT_DIR/network/list.txt" docker network ls

INSPECT_FORMAT='{"name":{{json .Name}},"image":{{json .Config.Image}},"status":{{json .State.Status}},"running":{{json .State.Running}},"exitCode":{{json .State.ExitCode}},"health":{{if .State.Health}}{{json .State.Health.Status}}{{else}}null{{end}}}'

for container in aegisops-postgres aegisops-redis aegisops-keycloak aegisops-agent aegisops-server aegisops-worker aegisops-runner; do
  capture "$OUTPUT_DIR/inspect/${container}.json" docker inspect --format "$INSPECT_FORMAT" "$container"
done

CONTAINER_NETWORKS_FORMAT='{{range $networkName, $network := .NetworkSettings.Networks}}{{println $networkName}}{{end}}'
NETWORK_INSPECT_FORMAT='{"name":{{json .Name}},"driver":{{json .Driver}},"scope":{{json .Scope}},"internal":{{json .Internal}},"attachable":{{json .Attachable}},"ingress":{{json .Ingress}},"containers":{{json .Containers}}}'
network_index=0
while IFS= read -r network_name; do
  [ -n "$network_name" ] || continue
  network_index=$((network_index + 1))
  capture "$OUTPUT_DIR/network/inspect-${network_index}.json" \
    docker network inspect --format "$NETWORK_INSPECT_FORMAT" "$network_name"
done < <(
  while IFS= read -r container_id; do
    [ -n "$container_id" ] || continue
    docker inspect --format "$CONTAINER_NETWORKS_FORMAT" "$container_id" 2>/dev/null || true
  done < <(compose ps -q -a 2>/dev/null || true) | sort -u
)

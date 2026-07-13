#!/usr/bin/env bash
set -u

COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.app.yml}"
OUTPUT_DIR="${OUTPUT_DIR:-runtime-diagnostics}"

mkdir -p "$OUTPUT_DIR/inspect" "$OUTPUT_DIR/network"

docker compose -f "$COMPOSE_FILE" ps -a >"$OUTPUT_DIR/compose-ps.txt" 2>&1 || true
docker compose -f "$COMPOSE_FILE" config >"$OUTPUT_DIR/compose-config.yml" 2>&1 || true
docker compose -f "$COMPOSE_FILE" logs --no-color >"$OUTPUT_DIR/compose.log" 2>&1 || true
docker network ls >"$OUTPUT_DIR/network/list.txt" 2>&1 || true

for container in aegisops-postgres aegisops-agent aegisops-server aegisops-worker aegisops-runner; do
  docker inspect "$container" >"$OUTPUT_DIR/inspect/${container}.json" 2>&1 || true
done

while IFS= read -r network_id; do
  [ -n "$network_id" ] || continue
  docker network inspect "$network_id" >"$OUTPUT_DIR/network/${network_id}.json" 2>&1 || true
done < <(docker network ls --filter name=ai-ops --format '{{.ID}}')

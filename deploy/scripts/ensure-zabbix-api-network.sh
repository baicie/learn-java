#!/usr/bin/env bash
set -Eeuo pipefail

NETWORK_NAME="aegisops-zabbix-api"

if ! command -v docker >/dev/null 2>&1; then
  echo "Required command is not available: docker" >&2
  exit 1
fi

inspect_network() {
  docker network inspect \
    --format '{{.Driver}} {{.Internal}}' \
    "$NETWORK_NAME"
}

network_config="$(inspect_network 2>/dev/null || true)"
if [ -z "$network_config" ]; then
  if ! docker network create --driver bridge --internal "$NETWORK_NAME" \
    >/dev/null 2>&1; then
    network_config="$(inspect_network 2>/dev/null || true)"
    if [ -z "$network_config" ]; then
      echo "Unable to create Docker network: $NETWORK_NAME" >&2
      exit 1
    fi
  else
    network_config="$(inspect_network)"
  fi
fi

if [ "$network_config" != "bridge true" ]; then
  echo "Docker network $NETWORK_NAME must be an internal bridge network; found: $network_config" >&2
  exit 1
fi

network_members="$(docker network inspect \
  --format '{{range .Containers}}{{println .Name}}{{end}}' \
  "$NETWORK_NAME")"
while IFS= read -r member_name; do
  [ -n "$member_name" ] || continue
  case "$member_name" in
    aegisops-app|aegisops-zabbix-web) ;;
    *)
      echo "Unexpected $NETWORK_NAME member: $member_name" >&2
      exit 1
      ;;
  esac
done <<< "$network_members"

#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/deploy/docker-compose.zabbix.yml}"
ENV_FILE="${ZABBIX_ENV_FILE:-$APP_DIR/deploy/.env.zabbix}"

require_command() {
  local command_name=$1
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is not available: ${command_name}" >&2
    exit 1
  fi
}

require_command docker
require_command openssl

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "Compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "==> Creating persistent Zabbix deployment environment"
  umask 077
  {
    printf 'ZABBIX_DB_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'ZABBIX_WEB_PORT=8083\n'
  } > "$ENV_FILE"
fi
chmod 600 "$ENV_FILE"

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

print_diagnostics() {
  echo "==> Zabbix deployment diagnostics"
  compose ps || true
  compose logs --no-color --tail=120 || true
}
trap print_diagnostics ERR

echo "==> Validating Zabbix deployment"
compose config --quiet

echo "==> Pulling Zabbix images"
compose pull

echo "==> Starting Zabbix"
compose up -d --remove-orphans --wait --wait-timeout 300
compose ps

echo "Zabbix is available on host port 8083."

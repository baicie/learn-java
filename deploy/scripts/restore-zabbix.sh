#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

backup_dir=""
execute=0
confirmation=""

usage() {
  cat >&2 <<'EOF'
Usage: restore-zabbix.sh --backup-dir PATH --execute --confirm RESTORE-ZABBIX

Without both confirmation flags, the command exits before invoking Docker.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --backup-dir)
      [ "$#" -ge 2 ] || { usage; exit 2; }
      backup_dir=$2
      shift 2
      ;;
    --execute)
      execute=1
      shift
      ;;
    --confirm)
      [ "$#" -ge 2 ] || { usage; exit 2; }
      confirmation=$2
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [ "$execute" != "1" ] || [ "$confirmation" != "RESTORE-ZABBIX" ]; then
  echo "Restore requires --execute --confirm RESTORE-ZABBIX." >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
BACKUP_SCRIPT="${ZABBIX_BACKUP_SCRIPT:-$SCRIPT_DIR/backup-zabbix.sh}"
ZABBIX_NETWORK_SCRIPT="${ZABBIX_NETWORK_SCRIPT:-$SCRIPT_DIR/ensure-zabbix-api-network.sh}"
BACKUP_ROOT="${ZABBIX_BACKUP_ROOT:-$APP_DIR/deploy/backups}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/deploy/docker-compose.zabbix.yml}"
ENV_FILE="${ZABBIX_ENV_FILE:-$APP_DIR/deploy/.env.zabbix}"
POSTGRES_CONTAINER="${ZABBIX_POSTGRES_CONTAINER:-aegisops-zabbix-postgres}"
SERVER_CONTAINER="${ZABBIX_SERVER_CONTAINER:-aegisops-zabbix-server}"
WEB_CONTAINER="${ZABBIX_WEB_CONTAINER:-aegisops-zabbix-web}"
AGENT2_CONTAINER="${ZABBIX_AGENT2_CONTAINER:-aegisops-zabbix-agent2}"
PULL_POLICY="${ZABBIX_RESTORE_PULL_POLICY:-always}"

case "$PULL_POLICY" in
  always|missing) ;;
  *)
    echo "Unsupported Zabbix restore pull policy: $PULL_POLICY" >&2
    exit 2
    ;;
esac

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is not available: $1" >&2
    exit 1
  }
}

for command_name in awk bash docker mktemp sha256sum; do
  require_command "$command_name"
done
if [ ! -f "$ZABBIX_NETWORK_SCRIPT" ]; then
  echo "Required deployment file is missing: $ZABBIX_NETWORK_SCRIPT" >&2
  exit 1
fi

if [ ! -d "$backup_dir" ] || [ ! -d "$BACKUP_ROOT" ]; then
  echo "Zabbix backup directory is missing: $backup_dir" >&2
  exit 1
fi
resolved_root="$(cd "$BACKUP_ROOT" && pwd -P)"
resolved_backup="$(cd "$backup_dir" && pwd -P)"
case "$resolved_backup/" in
  "$resolved_root"/*/) ;;
  *)
    echo "Backup must be a child of $resolved_root" >&2
    exit 1
    ;;
esac

for required_file in \
  docker-compose.zabbix.yml \
  docker-compose.rollback.yml \
  images.before.tsv \
  zabbix.dump \
  SHA256SUMS; do
  if [ ! -f "$resolved_backup/$required_file" ]; then
    echo "Required backup file is missing: $resolved_backup/$required_file" >&2
    exit 1
  fi
done
if [ ! -f "$ENV_FILE" ]; then
  echo "Current Zabbix secret environment is missing: $ENV_FILE" >&2
  exit 1
fi
(
  cd "$resolved_backup"
  sha256sum -c SHA256SUMS
)

bash "$ZABBIX_NETWORK_SCRIPT"
rescue_backup="$(APP_DIR="$APP_DIR" bash "$BACKUP_SCRIPT")"
echo "Current Zabbix state preserved at $rescue_backup"

compose_temp="$(mktemp "$APP_DIR/deploy/.restore-compose.XXXXXX")"
cleanup() {
  rm -f -- "$compose_temp"
}
trap cleanup EXIT
cp -- "$resolved_backup/docker-compose.zabbix.yml" "$compose_temp"
chmod 0600 "$compose_temp"

candidate_compose=(
  docker compose
  --env-file "$ENV_FILE"
  -f "$compose_temp"
  -f "$resolved_backup/docker-compose.rollback.yml"
)
"${candidate_compose[@]}" config --quiet
"${candidate_compose[@]}" pull --policy "$PULL_POLICY"

mv -- "$compose_temp" "$COMPOSE_FILE"
trap - EXIT

expected_image_id() {
  local container_name=$1
  awk -F '\t' -v container_name="$container_name" '
    $1 == container_name { print $3; matches++ }
    END { if (matches != 1) exit 1 }
  ' "$resolved_backup/images.before.tsv"
}

verify_container_image() {
  local container_name=$1
  local expected_image
  local actual_image

  if ! expected_image="$(expected_image_id "$container_name")"; then
    echo "Backup has no unique image record for $container_name" >&2
    exit 1
  fi
  actual_image="$(docker inspect --format '{{.Image}}' "$container_name")"
  if [ "$actual_image" != "$expected_image" ]; then
    echo "Rollback image mismatch for $container_name: expected $expected_image, found $actual_image" >&2
    exit 1
  fi
}

compose=(
  docker compose
  --env-file "$ENV_FILE"
  -f "$COMPOSE_FILE"
  -f "$resolved_backup/docker-compose.rollback.yml"
)
"${compose[@]}" down --remove-orphans
"${compose[@]}" up -d --wait zabbix-postgres
verify_container_image "$POSTGRES_CONTAINER"
docker exec "$POSTGRES_CONTAINER" \
  dropdb --username zabbix --if-exists --force zabbix
docker exec "$POSTGRES_CONTAINER" \
  createdb --username zabbix --owner zabbix zabbix
docker exec -i "$POSTGRES_CONTAINER" \
  pg_restore --username zabbix --dbname zabbix --no-owner --no-acl \
  --single-transaction --exit-on-error \
  < "$resolved_backup/zabbix.dump"
"${compose[@]}" up -d --remove-orphans --wait --wait-timeout 300
for container_name in \
  "$POSTGRES_CONTAINER" \
  "$SERVER_CONTAINER" \
  "$WEB_CONTAINER" \
  "$AGENT2_CONTAINER"; do
  verify_container_image "$container_name"
done
"${compose[@]}" ps
echo "Zabbix restore completed. Pre-restore rescue backup: $rescue_backup"

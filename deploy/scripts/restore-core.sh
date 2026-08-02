#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

backup_dir=""
execute=0
confirmation=""

usage() {
  cat >&2 <<'EOF'
Usage: restore-core.sh --backup-dir PATH --execute --confirm RESTORE-CORE

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

if [ "$execute" != "1" ] || [ "$confirmation" != "RESTORE-CORE" ]; then
  echo "Restore requires --execute --confirm RESTORE-CORE." >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
BACKUP_SCRIPT="${CORE_BACKUP_SCRIPT:-$SCRIPT_DIR/backup-core.sh}"
NETWORK_SCRIPT="${ZABBIX_NETWORK_SCRIPT:-$SCRIPT_DIR/ensure-zabbix-api-network.sh}"
BACKUP_ROOT="${CORE_BACKUP_ROOT:-$APP_DIR/deploy/backups}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/deploy/docker-compose.core.yml}"
RUNTIME_DIR="${AIOPS_RUNTIME_DIR:-$APP_DIR/deploy/runtime}"
ENV_FILE="$RUNTIME_DIR/.env"
POSTGRES_CONTAINER="${CORE_POSTGRES_CONTAINER:-aegisops-postgres}"
APP_CONTAINER="${CORE_APP_CONTAINER:-aegisops-app}"
AGENT_CONTAINER="${CORE_AGENT_CONTAINER:-aegisops-agent}"
RUNNER_CONTAINER="${CORE_RUNNER_CONTAINER:-aegisops-runner}"
POSTGRES_VOLUME="${CORE_POSTGRES_VOLUME:-aegisops_postgres_data}"
PULL_POLICY="${CORE_RESTORE_PULL_POLICY:-always}"

case "$PULL_POLICY" in
  always|missing) ;;
  *)
    echo "Unsupported Core restore pull policy: $PULL_POLICY" >&2
    exit 2
    ;;
esac

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is not available: $1" >&2
    exit 1
  }
}

for command_name in awk bash docker mktemp sha256sum tr; do
  require_command "$command_name"
done
for required_file in "$BACKUP_SCRIPT" "$NETWORK_SCRIPT" "$ENV_FILE"; do
  if [ ! -f "$required_file" ]; then
    echo "Required Core restore file is missing: $required_file" >&2
    exit 1
  fi
done
if [ ! -d "$backup_dir" ] || [ ! -d "$BACKUP_ROOT" ]; then
  echo "Core backup directory is missing: $backup_dir" >&2
  exit 1
fi
resolved_root="$(cd "$BACKUP_ROOT" && pwd -P)"
resolved_backup="$(cd "$backup_dir" && pwd -P)"
if [ "$(dirname "$resolved_backup")" != "$resolved_root" ]; then
  echo "Core backup must be a direct child of $resolved_root" >&2
  exit 1
fi
for required_file in \
  docker-compose.core.yml \
  docker-compose.rollback.yml \
  deploy-mode \
  images.before.tsv \
  core.dump \
  SHA256SUMS; do
  if [ ! -f "$resolved_backup/$required_file" ]; then
    echo "Required Core backup file is missing: $resolved_backup/$required_file" >&2
    exit 1
  fi
done
(
  cd "$resolved_backup"
  sha256sum -c SHA256SUMS
)
deploy_mode="$(tr -d '\r\n' < "$resolved_backup/deploy-mode")"
case "$deploy_mode" in
  core|diagnostic|automation) ;;
  *)
    echo "Core backup contains an invalid deployment mode: $deploy_mode" >&2
    exit 1
    ;;
esac

bash "$NETWORK_SCRIPT"
rescue_backup="$(
  APP_DIR="$APP_DIR" \
  COMPOSE_FILE="$COMPOSE_FILE" \
  CORE_BACKUP_ROOT="$BACKUP_ROOT" \
  CORE_POSTGRES_CONTAINER="$POSTGRES_CONTAINER" \
  CORE_APP_CONTAINER="$APP_CONTAINER" \
  CORE_AGENT_CONTAINER="$AGENT_CONTAINER" \
  CORE_RUNNER_CONTAINER="$RUNNER_CONTAINER" \
  CORE_POSTGRES_VOLUME="$POSTGRES_VOLUME" \
  bash "$BACKUP_SCRIPT"
)"
echo "Current Core state preserved at $rescue_backup"

compose_temp="$(mktemp "$APP_DIR/deploy/.restore-core-compose.XXXXXX")"
cleanup() {
  rm -f -- "$compose_temp"
}
trap cleanup EXIT
cp -- "$resolved_backup/docker-compose.core.yml" "$compose_temp"
chmod 0600 "$compose_temp"

candidate_compose=(
  docker compose
  --env-file "$ENV_FILE"
  -f "$compose_temp"
  -f "$resolved_backup/docker-compose.rollback.yml"
)
case "$deploy_mode" in
  diagnostic) candidate_compose+=(--profile ai) ;;
  automation) candidate_compose+=(--profile ai --profile automation) ;;
esac

"${candidate_compose[@]}" config --quiet
"${candidate_compose[@]}" pull --policy "$PULL_POLICY"

mv -- "$compose_temp" "$COMPOSE_FILE"
trap - EXIT

compose=(
  docker compose
  --env-file "$ENV_FILE"
  -f "$COMPOSE_FILE"
  -f "$resolved_backup/docker-compose.rollback.yml"
)
case "$deploy_mode" in
  diagnostic) compose+=(--profile ai) ;;
  automation) compose+=(--profile ai --profile automation) ;;
esac

expected_image_id() {
  local container_name=$1
  awk -F '\t' -v container_name="$container_name" '
    $1 == container_name { print $4; matches++ }
    END { if (matches != 1) exit 1 }
  ' "$resolved_backup/images.before.tsv"
}

verify_container_image() {
  local container_name=$1
  local expected_image
  local actual_image
  expected_image="$(expected_image_id "$container_name")" || {
    echo "Core backup has no unique image record for $container_name" >&2
    exit 1
  }
  actual_image="$(docker inspect --format '{{.Image}}' "$container_name")"
  if [ "$actual_image" != "$expected_image" ]; then
    echo "Core rollback image mismatch for $container_name: expected $expected_image, found $actual_image" >&2
    exit 1
  fi
}

"${compose[@]}" down --remove-orphans
"${compose[@]}" up -d --wait postgres
verify_container_image "$POSTGRES_CONTAINER"
docker exec "$POSTGRES_CONTAINER" sh -ec '
  dropdb --username "$POSTGRES_USER" --if-exists --force "$POSTGRES_DB"
  createdb --username "$POSTGRES_USER" --owner "$POSTGRES_USER" "$POSTGRES_DB"
'
docker exec -i "$POSTGRES_CONTAINER" sh -ec '
  exec pg_restore \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --no-owner \
    --no-acl \
    --single-transaction \
    --exit-on-error
' < "$resolved_backup/core.dump"
"${compose[@]}" up -d --remove-orphans --wait --wait-timeout 300
while IFS=$'\t' read -r container_name _; do
  [ -n "$container_name" ] || continue
  verify_container_image "$container_name"
done < "$resolved_backup/images.before.tsv"
bash "$NETWORK_SCRIPT"
"${compose[@]}" ps
echo "Core restore completed. Pre-restore rescue backup: $rescue_backup"

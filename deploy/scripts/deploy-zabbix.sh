#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/deploy/docker-compose.zabbix.yml}"
ENV_FILE="${ZABBIX_ENV_FILE:-$APP_DIR/deploy/.env.zabbix}"
ZABBIX_NETWORK_SCRIPT="${ZABBIX_NETWORK_SCRIPT:-$SCRIPT_DIR/ensure-zabbix-api-network.sh}"
ZABBIX_BACKUP_SCRIPT="${ZABBIX_BACKUP_SCRIPT:-$SCRIPT_DIR/backup-zabbix.sh}"
ZABBIX_RESTORE_SCRIPT="${ZABBIX_RESTORE_SCRIPT:-$SCRIPT_DIR/restore-zabbix.sh}"
ZABBIX_PREDEPLOY_BACKUP_DIR="${ZABBIX_PREDEPLOY_BACKUP_DIR:-}"
ZABBIX_BACKUP_ROOT="${ZABBIX_BACKUP_ROOT:-$APP_DIR/deploy/backups}"
ZABBIX_API_NETWORK_NAME="aegisops-zabbix-api"
POSTGRES_CONTAINER="${ZABBIX_POSTGRES_CONTAINER:-aegisops-zabbix-postgres}"
SERVER_CONTAINER="${ZABBIX_SERVER_CONTAINER:-aegisops-zabbix-server}"
WEB_CONTAINER="${ZABBIX_WEB_CONTAINER:-aegisops-zabbix-web}"
AGENT2_CONTAINER="${ZABBIX_AGENT2_CONTAINER:-aegisops-zabbix-agent2}"

require_command() {
  local command_name=$1
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is not available: ${command_name}" >&2
    exit 1
  fi
}

require_command docker
require_command openssl
require_command awk
require_command sha256sum

for required_script in \
  "$ZABBIX_NETWORK_SCRIPT" \
  "$ZABBIX_BACKUP_SCRIPT" \
  "$ZABBIX_RESTORE_SCRIPT"; do
  if [ ! -f "$required_script" ]; then
    echo "Required deployment file is missing: $required_script" >&2
    exit 1
  fi
done

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "Compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

validate_predeploy_backup() {
  local candidate_backup=$1
  local resolved_root
  local resolved_backup
  local container_name
  local expected_image
  local actual_image

  if [ ! -d "$candidate_backup" ] || [ ! -d "$ZABBIX_BACKUP_ROOT" ]; then
    echo "Zabbix pre-deployment backup is missing: $candidate_backup" >&2
    return 1
  fi
  resolved_root="$(cd "$ZABBIX_BACKUP_ROOT" && pwd -P)"
  resolved_backup="$(cd "$candidate_backup" && pwd -P)"
  case "$resolved_backup/" in
    "$resolved_root"/*/) ;;
    *)
      echo "Zabbix pre-deployment backup must be below $resolved_root" >&2
      return 1
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
      return 1
    fi
  done
  (
    cd "$resolved_backup"
    sha256sum -c SHA256SUMS >/dev/null
  )
  for container_name in \
    "$POSTGRES_CONTAINER" \
    "$SERVER_CONTAINER" \
    "$WEB_CONTAINER" \
    "$AGENT2_CONTAINER"; do
    if ! expected_image="$(awk -F '\t' -v container_name="$container_name" '
      $1 == container_name { print $3; matches++ }
      END { if (matches != 1) exit 1 }
    ' "$resolved_backup/images.before.tsv")"; then
      echo "Backup has no unique image record for $container_name" >&2
      return 1
    fi
    actual_image="$(docker inspect --format '{{.Image}}' "$container_name")"
    if [ "$actual_image" != "$expected_image" ]; then
      echo "Backup image mismatch for $container_name: expected $expected_image, found $actual_image" >&2
      return 1
    fi
  done
  printf '%s\n' "$resolved_backup"
}

run_backup_script() {
  APP_DIR="$APP_DIR" \
    COMPOSE_FILE="$COMPOSE_FILE" \
    ZABBIX_ENV_FILE="$ENV_FILE" \
    ZABBIX_BACKUP_ROOT="$ZABBIX_BACKUP_ROOT" \
    ZABBIX_POSTGRES_CONTAINER="$POSTGRES_CONTAINER" \
    ZABBIX_SERVER_CONTAINER="$SERVER_CONTAINER" \
    ZABBIX_WEB_CONTAINER="$WEB_CONTAINER" \
    ZABBIX_AGENT2_CONTAINER="$AGENT2_CONTAINER" \
    bash "$ZABBIX_BACKUP_SCRIPT" "$@"
}

validate_network_members_before_deploy() {
  local actual_members
  local expected_members

  actual_members="$(docker network inspect \
    --format '{{range .Containers}}{{println .Name}}{{end}}' \
    "$ZABBIX_API_NETWORK_NAME" | sed '/^$/d' | sort)"
  expected_members="$(printf '%s\n' aegisops-app aegisops-zabbix-web | sort)"
  if [ "$actual_members" != "aegisops-app" ] \
    && [ "$actual_members" != "$expected_members" ]; then
    echo "Unexpected $ZABBIX_API_NETWORK_NAME members before deployment:" >&2
    printf '%s\n' "$actual_members" >&2
    exit 1
  fi
}

print_diagnostics() {
  echo "==> Zabbix deployment diagnostics"
  compose ps || true
  compose logs --no-color --tail=120 || true
}
trap print_diagnostics ERR

bash "$ZABBIX_NETWORK_SCRIPT"
validate_network_members_before_deploy
zabbix_state="$(run_backup_script --probe-only)"
backup_dir=""
case "$zabbix_state" in
  running)
    if [ -n "$ZABBIX_PREDEPLOY_BACKUP_DIR" ]; then
      echo "==> Validating staged Zabbix pre-deployment backup"
      backup_dir="$(validate_predeploy_backup "$ZABBIX_PREDEPLOY_BACKUP_DIR")"
    else
      echo "==> Backing up Zabbix PostgreSQL before deployment"
      backup_dir="$(run_backup_script)"
      backup_dir="$(validate_predeploy_backup "$backup_dir")"
    fi
    echo "Validated Zabbix backup: $backup_dir"
    ;;
  empty)
    if [ -n "$ZABBIX_PREDEPLOY_BACKUP_DIR" ]; then
      echo "A pre-deployment backup was supplied but no existing Zabbix state was found." >&2
      exit 1
    fi
    ;;
  *)
    echo "Unexpected Zabbix state probe result: $zabbix_state" >&2
    exit 1
    ;;
esac

if [ ! -f "$ENV_FILE" ]; then
  echo "==> Creating persistent Zabbix deployment environment"
  umask 077
  {
    printf 'ZABBIX_DB_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'ZABBIX_BIND_ADDRESS=127.0.0.1\n'
    printf 'ZABBIX_WEB_PORT=8083\n'
    printf 'ZABBIX_SERVER_PORT=10051\n'
  } > "$ENV_FILE"
fi
chmod 600 "$ENV_FILE"
echo "==> Validating Zabbix deployment"
compose config --quiet

echo "==> Pulling Zabbix images"
compose pull

echo "==> Starting Zabbix"
compose up -d --remove-orphans --wait --wait-timeout 300
compose ps

if [ -n "$backup_dir" ]; then
  : > "$backup_dir/images.after.tsv"
  for container_name in \
    "$POSTGRES_CONTAINER" \
    "$SERVER_CONTAINER" \
    "$WEB_CONTAINER" \
    "$AGENT2_CONTAINER"; do
    image_id="$(docker inspect --format '{{.Image}}' "$container_name")"
    configured_image="$(docker inspect --format '{{.Config.Image}}' "$container_name")"
    repo_digests="$(docker image inspect \
      --format '{{range .RepoDigests}}{{println .}}{{end}}' \
      "$image_id")"
    repo_digests="${repo_digests//$'\n'/,}"
    printf '%s\t%s\t%s\t%s\n' \
      "$container_name" "$configured_image" "$image_id" "$repo_digests" \
      >> "$backup_dir/images.after.tsv"
  done
  (
    cd "$backup_dir"
    sha256sum images.after.tsv >> SHA256SUMS
  )
  echo "Rollback command: bash $ZABBIX_RESTORE_SCRIPT --backup-dir $backup_dir --execute --confirm RESTORE-ZABBIX"
fi

web_binding="$(compose port zabbix-web 8080)"
server_binding="$(compose port zabbix-server 10051)"
echo "Zabbix Web is available on ${web_binding}."
echo "Zabbix Server is available on ${server_binding}."

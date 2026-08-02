#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/deploy/docker-compose.zabbix.yml}"
ENV_FILE="${ZABBIX_ENV_FILE:-$APP_DIR/deploy/.env.zabbix}"
BACKUP_ROOT="${ZABBIX_BACKUP_ROOT:-$APP_DIR/deploy/backups}"
POSTGRES_CONTAINER="${ZABBIX_POSTGRES_CONTAINER:-aegisops-zabbix-postgres}"
SERVER_CONTAINER="${ZABBIX_SERVER_CONTAINER:-aegisops-zabbix-server}"
WEB_CONTAINER="${ZABBIX_WEB_CONTAINER:-aegisops-zabbix-web}"
AGENT2_CONTAINER="${ZABBIX_AGENT2_CONTAINER:-aegisops-zabbix-agent2}"
POSTGRES_VOLUME="${ZABBIX_POSTGRES_VOLUME:-aegisops-zabbix_zabbix_postgres_data}"
probe_only=0

if [ "${1:-}" = "--probe-only" ] && [ "$#" -eq 1 ]; then
  probe_only=1
elif [ "$#" -ne 0 ]; then
  echo "Usage: backup-zabbix.sh [--probe-only]" >&2
  exit 2
fi

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is not available: $1" >&2
    exit 1
  }
}

require_command docker

probe_existing_state() {
  local container_running
  local labeled_volumes

  container_running="$(docker inspect \
    --format '{{.State.Running}}' "$POSTGRES_CONTAINER" 2>/dev/null || true)"
  case "$container_running" in
    true)
      printf 'running\n'
      return 0
      ;;
    false)
      echo "Existing Zabbix PostgreSQL container is not running and cannot be backed up safely: $POSTGRES_CONTAINER" >&2
      return 1
      ;;
    '') ;;
    *)
      echo "Unable to determine Zabbix PostgreSQL state safely: $container_running" >&2
      return 1
      ;;
  esac

  if docker volume inspect "$POSTGRES_VOLUME" >/dev/null 2>&1; then
    echo "Existing Zabbix PostgreSQL volume cannot be backed up safely without a running container: $POSTGRES_VOLUME" >&2
    return 1
  fi
  labeled_volumes="$(docker volume ls --quiet \
    --filter label=com.docker.compose.project=aegisops-zabbix \
    --filter label=com.docker.compose.volume=zabbix_postgres_data)"
  if [ -n "$labeled_volumes" ]; then
    echo "Existing Zabbix PostgreSQL volume cannot be backed up safely without a running container: $labeled_volumes" >&2
    return 1
  fi

  printf 'empty\n'
}

existing_state="$(probe_existing_state)"
if [ "$probe_only" = "1" ]; then
  printf '%s\n' "$existing_state"
  exit 0
fi
if [ "$existing_state" != "running" ]; then
  echo "No running Zabbix PostgreSQL state is available to back up." >&2
  exit 1
fi

for command_name in mktemp sha256sum; do
  require_command "$command_name"
done

for required_file in "$COMPOSE_FILE" "$ENV_FILE"; do
  if [ ! -f "$required_file" ]; then
    echo "Required Zabbix deployment file is missing: $required_file" >&2
    exit 1
  fi
done

mkdir -p "$BACKUP_ROOT"
chmod 0700 "$BACKUP_ROOT"
temporary_dir="$(mktemp -d "$BACKUP_ROOT/.zabbix-backup.XXXXXX")"
backup_dir="$BACKUP_ROOT/zabbix-$(date -u +%Y%m%dT%H%M%SZ)-$$"
cleanup() {
  if [ -d "$temporary_dir" ]; then
    rm -rf -- "$temporary_dir"
  fi
}
trap cleanup EXIT

cp -p -- "$COMPOSE_FILE" "$temporary_dir/docker-compose.zabbix.yml"
chmod 0600 "$temporary_dir/docker-compose.zabbix.yml"

containers=(
  "$POSTGRES_CONTAINER"
  "$SERVER_CONTAINER"
  "$WEB_CONTAINER"
  "$AGENT2_CONTAINER"
)
services=(
  zabbix-postgres
  zabbix-server
  zabbix-web
  zabbix-agent2
)
: > "$temporary_dir/images.before.tsv"
printf 'services:\n' > "$temporary_dir/docker-compose.rollback.yml"
for container_index in "${!containers[@]}"; do
  container_name="${containers[$container_index]}"
  service_name="${services[$container_index]}"
  image_id="$(docker inspect --format '{{.Image}}' "$container_name")"
  configured_image="$(docker inspect --format '{{.Config.Image}}' "$container_name")"
  repo_digests="$(docker image inspect \
    --format '{{range .RepoDigests}}{{println .}}{{end}}' \
    "$image_id")"
  repo_digests="${repo_digests//$'\n'/,}"
  exact_image="${repo_digests%%,*}"
  if [ -z "$exact_image" ]; then
    exact_image="$image_id"
  fi
  case "$exact_image" in
    ''|*[!A-Za-z0-9._/@:-]*)
      echo "Unsafe rollback image reference for $container_name: $exact_image" >&2
      exit 1
      ;;
  esac
  printf '%s\t%s\t%s\t%s\n' \
    "$container_name" "$configured_image" "$image_id" "$repo_digests" \
    >> "$temporary_dir/images.before.tsv"
  printf '  %s:\n    image: "%s"\n' \
    "$service_name" "$exact_image" \
    >> "$temporary_dir/docker-compose.rollback.yml"
done

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  -f "$temporary_dir/docker-compose.rollback.yml" \
  config --quiet

docker exec "$POSTGRES_CONTAINER" \
  pg_dump --username zabbix --dbname zabbix --format=custom --no-owner --no-acl \
  > "$temporary_dir/zabbix.dump"
docker exec -i "$POSTGRES_CONTAINER" pg_restore --list \
  < "$temporary_dir/zabbix.dump" >/dev/null

(
  cd "$temporary_dir"
  sha256sum \
    docker-compose.zabbix.yml \
    docker-compose.rollback.yml \
    images.before.tsv \
    zabbix.dump \
    > SHA256SUMS
)
chmod 0600 "$temporary_dir"/*
mv -- "$temporary_dir" "$backup_dir"
trap - EXIT
printf '%s\n' "$backup_dir"

#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/deploy/docker-compose.core.yml}"
BACKUP_ROOT="${CORE_BACKUP_ROOT:-$APP_DIR/deploy/backups}"
POSTGRES_CONTAINER="${CORE_POSTGRES_CONTAINER:-aegisops-postgres}"
APP_CONTAINER="${CORE_APP_CONTAINER:-aegisops-app}"
AGENT_CONTAINER="${CORE_AGENT_CONTAINER:-aegisops-agent}"
RUNNER_CONTAINER="${CORE_RUNNER_CONTAINER:-aegisops-runner}"
POSTGRES_VOLUME="${CORE_POSTGRES_VOLUME:-aegisops_postgres_data}"
probe_only=0
validate_dir=""

usage() {
  cat >&2 <<'EOF'
Usage: backup-core.sh [--probe-only | --validate PATH]
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --probe-only)
      [ "$#" -eq 1 ] || { usage; exit 2; }
      probe_only=1
      shift
      ;;
    --validate)
      [ "$#" -eq 2 ] || { usage; exit 2; }
      validate_dir=$2
      shift 2
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is not available: $1" >&2
    exit 1
  }
}

for command_name in awk docker mktemp sha256sum; do
  require_command "$command_name"
done

probe_existing_state() {
  local container_running
  local container_project
  local container_service
  local postgres_user
  local labeled_volumes
  container_running="$(docker inspect \
    --format '{{.State.Running}}' "$POSTGRES_CONTAINER" 2>/dev/null || true)"
  case "$container_running" in
    true)
      if [ "$POSTGRES_CONTAINER" = "aegisops-postgres" ]; then
        container_project="$(docker inspect --format \
          '{{ index .Config.Labels "com.docker.compose.project" }}' \
          "$POSTGRES_CONTAINER")"
        container_service="$(docker inspect --format \
          '{{ index .Config.Labels "com.docker.compose.service" }}' \
          "$POSTGRES_CONTAINER")"
        postgres_user="$(docker inspect --format \
          '{{range .Config.Env}}{{println .}}{{end}}' \
          "$POSTGRES_CONTAINER" \
          | awk -F= '$1 == "POSTGRES_USER" {sub(/^[^=]*=/, ""); print; exit}')"
        if [ "$container_service" != "postgres" ]; then
          echo "Container $POSTGRES_CONTAINER is not a Compose PostgreSQL service; refusing backup." >&2
          return 1
        fi
        if [ "$container_project" = "aegisops" ] \
          && [ "$postgres_user" = "aegisops_admin" ]; then
          printf 'running\n'
          return 0
        fi
        case "$container_project:$postgres_user" in
          deploy:aegisops|infra:aegisops|aegisops-core:aegisops)
            printf 'legacy\n'
            return 0
            ;;
        esac
        echo "Existing PostgreSQL identity is neither current Core nor a supported legacy topology: project=${container_project:-<none>}, user=${postgres_user:-<none>}" >&2
        return 1
      fi
      printf 'running\n'
      return 0
      ;;
    false)
      echo "Existing Core PostgreSQL container is not running and cannot be backed up safely: $POSTGRES_CONTAINER" >&2
      return 1
      ;;
    '') ;;
    *)
      echo "Unable to determine Core PostgreSQL state safely: $container_running" >&2
      return 1
      ;;
  esac

  if docker volume inspect "$POSTGRES_VOLUME" >/dev/null 2>&1; then
    echo "Existing Core PostgreSQL volume cannot be backed up safely without a running container: $POSTGRES_VOLUME" >&2
    return 1
  fi
  labeled_volumes="$(docker volume ls --quiet \
    --filter label=com.docker.compose.project=aegisops \
    --filter label=com.docker.compose.volume=core_postgres_data)"
  if [ -n "$labeled_volumes" ]; then
    echo "Existing Core PostgreSQL volume cannot be backed up safely without a running container: $labeled_volumes" >&2
    return 1
  fi
  printf 'empty\n'
}

resolve_backup() {
  local candidate=$1
  local resolved_root
  local resolved_backup
  if [ ! -d "$candidate" ] || [ ! -d "$BACKUP_ROOT" ]; then
    echo "Core backup directory is missing: $candidate" >&2
    return 1
  fi
  resolved_root="$(cd "$BACKUP_ROOT" && pwd -P)"
  resolved_backup="$(cd "$candidate" && pwd -P)"
  if [ "$(dirname "$resolved_backup")" != "$resolved_root" ]; then
    echo "Core backup must be a direct child of $resolved_root" >&2
    return 1
  fi
  printf '%s\n' "$resolved_backup"
}

validate_backup() {
  local candidate=$1
  local resolved_backup
  local container_name
  local expected_image
  local actual_image
  resolved_backup="$(resolve_backup "$candidate")" || return 1
  for required_file in \
    docker-compose.core.yml \
    docker-compose.rollback.yml \
    deploy-mode \
    images.before.tsv \
    core.dump \
    SHA256SUMS; do
    if [ ! -f "$resolved_backup/$required_file" ]; then
      echo "Required Core backup file is missing: $resolved_backup/$required_file" >&2
      return 1
    fi
  done
  (
    cd "$resolved_backup"
    sha256sum -c SHA256SUMS >/dev/null
  ) || return 1
  while IFS=$'\t' read -r container_name _ _ expected_image _; do
    [ -n "$container_name" ] || continue
    actual_image="$(docker inspect --format '{{.Image}}' "$container_name")" || return 1
    if [ "$actual_image" != "$expected_image" ]; then
      echo "Core backup image mismatch for $container_name: expected $expected_image, found $actual_image" >&2
      return 1
    fi
  done < "$resolved_backup/images.before.tsv"
  printf '%s\n' "$resolved_backup"
}

if [ -n "$validate_dir" ]; then
  validate_backup "$validate_dir"
  exit 0
fi

existing_state="$(probe_existing_state)"
if [ "$probe_only" = "1" ]; then
  printf '%s\n' "$existing_state"
  exit 0
fi
if [ "$existing_state" != "running" ]; then
  echo "No running Core PostgreSQL state is available to back up." >&2
  exit 1
fi
if [ ! -f "$COMPOSE_FILE" ]; then
  echo "Required Core deployment file is missing: $COMPOSE_FILE" >&2
  exit 1
fi

containers=("$POSTGRES_CONTAINER" "$APP_CONTAINER")
services=(postgres aegisops-app)
deploy_mode=core
if docker inspect "$AGENT_CONTAINER" >/dev/null 2>&1; then
  containers+=("$AGENT_CONTAINER")
  services+=(aiops-agent)
  deploy_mode=diagnostic
fi
if docker inspect "$RUNNER_CONTAINER" >/dev/null 2>&1; then
  containers+=("$RUNNER_CONTAINER")
  services+=(aiops-runner)
  deploy_mode=automation
fi
for container_name in "${containers[@]}"; do
  if [ "$(docker inspect --format '{{.State.Running}}' "$container_name")" != "true" ]; then
    echo "Core container is not running and cannot be backed up safely: $container_name" >&2
    exit 1
  fi
done

mkdir -p "$BACKUP_ROOT"
chmod 0700 "$BACKUP_ROOT"
temporary_dir="$(mktemp -d "$BACKUP_ROOT/.core-backup.XXXXXX")"
backup_dir="$BACKUP_ROOT/core-$(date -u +%Y%m%dT%H%M%SZ)-$$"
cleanup() {
  if [ -d "$temporary_dir" ]; then
    rm -rf -- "$temporary_dir"
  fi
}
trap cleanup EXIT

cp -p -- "$COMPOSE_FILE" "$temporary_dir/docker-compose.core.yml"
printf '%s\n' "$deploy_mode" > "$temporary_dir/deploy-mode"
: > "$temporary_dir/images.before.tsv"
printf 'services:\n' > "$temporary_dir/docker-compose.rollback.yml"
for container_index in "${!containers[@]}"; do
  container_name="${containers[$container_index]}"
  service_name="${services[$container_index]}"
  image_id="$(docker inspect --format '{{.Image}}' "$container_name")"
  configured_image="$(docker inspect --format '{{.Config.Image}}' "$container_name")"
  repo_digests="$(docker image inspect \
    --format '{{range .RepoDigests}}{{println .}}{{end}}' "$image_id")"
  repo_digests="${repo_digests//$'\n'/,}"
  exact_image="${repo_digests%%,*}"
  if [ -z "$exact_image" ]; then
    exact_image="$image_id"
  fi
  case "$exact_image" in
    ''|*[!A-Za-z0-9._/@:-]*)
      echo "Unsafe Core rollback image reference for $container_name: $exact_image" >&2
      exit 1
      ;;
  esac
  printf '%s\t%s\t%s\t%s\t%s\n' \
    "$container_name" "$service_name" "$configured_image" "$image_id" "$repo_digests" \
    >> "$temporary_dir/images.before.tsv"
  printf '  %s:\n    image: "%s"\n' "$service_name" "$exact_image" \
    >> "$temporary_dir/docker-compose.rollback.yml"
done

docker exec "$POSTGRES_CONTAINER" sh -ec '
  exec pg_dump \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --format=custom \
    --no-owner \
    --no-acl
' > "$temporary_dir/core.dump"
docker exec -i "$POSTGRES_CONTAINER" pg_restore --list \
  < "$temporary_dir/core.dump" >/dev/null

(
  cd "$temporary_dir"
  sha256sum \
    docker-compose.core.yml \
    docker-compose.rollback.yml \
    deploy-mode \
    images.before.tsv \
    core.dump \
    > SHA256SUMS
)
chmod 0600 "$temporary_dir"/*
mv -- "$temporary_dir" "$backup_dir"
trap - EXIT
printf '%s\n' "$backup_dir"

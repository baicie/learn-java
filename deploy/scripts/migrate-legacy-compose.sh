#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RUNTIME_DIR="${AIOPS_RUNTIME_DIR:-$DEPLOY_DIR/runtime}"
ENV_FILE="$RUNTIME_DIR/.env"
SECRETS_DIR="$RUNTIME_DIR/secrets"
STATE_FILE="$RUNTIME_DIR/.legacy-migration.env"
FINALIZED_STATE_FILE="$RUNTIME_DIR/legacy-migration.finalized"
LEGACY_POSTGRES_CONTAINER="${AIOPS_LEGACY_POSTGRES_CONTAINER:-}"
EXPECTED_LEGACY_PROJECT="${AIOPS_LEGACY_COMPOSE_PROJECT:-}"
ACTION="${1:-prepare}"

case "$ACTION" in
  prepare|--finalize) ;;
  *)
    echo "Usage: migrate-legacy-compose.sh [prepare|--finalize]" >&2
    exit 2
    ;;
esac

container_exists() {
  local container=$1
  [ -n "$container" ] \
    && [ -n "$(docker inspect --format '{{.Id}}' "$container" 2>/dev/null || true)" ]
}

container_label() {
  local container=$1
  local label=$2
  docker inspect --format "{{ index .Config.Labels \"$label\" }}" "$container"
}

container_env_value() {
  local container=$1
  local name=$2
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' \
    "$container" \
    | awk -F= -v expected="$name" '$1 == expected {sub(/^[^=]*=/, ""); print; exit}'
}

read_state_value() {
  local key=$1
  awk -F= -v expected="$key" '$1 == expected {sub(/^[^=]*=/, ""); print; exit}' "$STATE_FILE"
}

service_is_legacy_component() {
  case "$1" in
    postgres|aiops-server|aiops-worker|aiops-agent|aiops-runner) return 0 ;;
    *) return 1 ;;
  esac
}

quiesce_legacy_containers() {
  local compose_project=$1
  local migration_id=$2
  local container_ids=$3
  local container_id container_project service running container_name renamed
  local ids=()

  IFS=',' read -r -a ids <<< "$container_ids"
  for container_id in "${ids[@]}"; do
    [ -n "$container_id" ] || continue
    container_exists "$container_id" || continue
    container_project="$(container_label "$container_id" com.docker.compose.project)"
    service="$(container_label "$container_id" com.docker.compose.service)"
    if [ "$container_project" != "$compose_project" ] || ! service_is_legacy_component "$service"; then
      echo "Recorded container $container_id no longer matches legacy project/service; refusing migration." >&2
      exit 1
    fi
    running="$(docker inspect --format '{{.State.Running}}' "$container_id")"
    if [ "$running" = "true" ]; then
      docker stop --time 30 "$container_id" >/dev/null
    fi
    container_name="$(docker inspect --format '{{.Name}}' "$container_id" | sed 's#^/##')"
    case "$container_name" in
      aegisops-postgres|aegisops-app|aegisops-agent|aegisops-runner)
        renamed="${container_name}-legacy-${migration_id}"
        if ! container_exists "$renamed"; then
          docker rename "$container_id" "$renamed" >/dev/null
        fi
        ;;
    esac
  done
}

remove_legacy_containers() {
  local compose_project=$1
  local container_ids=$2
  local container_id container_project service
  local ids=()

  IFS=',' read -r -a ids <<< "$container_ids"
  for container_id in "${ids[@]}"; do
    [ -n "$container_id" ] || continue
    container_exists "$container_id" || continue
    container_project="$(container_label "$container_id" com.docker.compose.project)"
    service="$(container_label "$container_id" com.docker.compose.service)"
    if [ "$container_project" != "$compose_project" ] || ! service_is_legacy_component "$service"; then
      echo "Recorded container $container_id no longer matches legacy project/service; refusing cleanup." >&2
      exit 1
    fi
    docker rm "$container_id" >/dev/null
  done
}

if [ "$ACTION" = "--finalize" ]; then
  if [ ! -s "$STATE_FILE" ]; then
    exit 0
  fi
  if [ "${AIOPS_TARGET_STACK_HEALTHY:-false}" != "true" ]; then
    echo "Target stack health confirmation is required; legacy credentials remain available for rollback." >&2
    exit 1
  fi
  for required_file in \
    "$SECRETS_DIR/postgres_admin_password" \
    "$DEPLOY_DIR/init/004-finalize-legacy-role.sql"; do
    if [ ! -s "$required_file" ]; then
      echo "Legacy finalization prerequisite is missing or empty: $required_file" >&2
      exit 1
    fi
  done

  target_postgres="${AIOPS_TARGET_POSTGRES_CONTAINER:-aegisops-postgres}"
  if ! container_exists "$target_postgres"; then
    echo "Target PostgreSQL container is not running: $target_postgres" >&2
    exit 1
  fi
  if [ "$(container_label "$target_postgres" com.docker.compose.service)" != "postgres" ] \
    || [ "$(container_env_value "$target_postgres" POSTGRES_USER)" != "aegisops_admin" ]; then
    echo "Target container $target_postgres is not the new AegisOps PostgreSQL service; refusing finalization." >&2
    exit 1
  fi
  target_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$target_postgres")"
  if [ "$target_health" != "healthy" ]; then
    echo "Target PostgreSQL is not healthy ($target_health); legacy credentials remain available for rollback." >&2
    exit 1
  fi

  legacy_user="$(read_state_value AIOPS_LEGACY_DB_OWNER)"
  legacy_database="$(read_state_value AIOPS_LEGACY_DB_NAME)"
  compose_project="$(read_state_value AIOPS_LEGACY_COMPOSE_PROJECT)"
  legacy_container_ids="$(read_state_value AIOPS_LEGACY_CONTAINER_IDS)"
  migration_id="$(read_state_value AIOPS_LEGACY_MIGRATION_ID)"
  if [ "$legacy_user" != "aegisops" ] || [ "$legacy_database" != "aegisops" ] \
    || [ -z "$compose_project" ] || [ -z "$legacy_container_ids" ] || [ -z "$migration_id" ]; then
    echo "Legacy migration state is invalid; refusing finalization." >&2
    exit 1
  fi

  quiesce_legacy_containers "$compose_project" "$migration_id" "$legacy_container_ids"

  finalize_credentials="$(mktemp "$RUNTIME_DIR/.legacy-finalize.XXXXXX")"
  finalize_cleanup() {
    rm -f -- "$finalize_credentials"
  }
  trap finalize_cleanup EXIT HUP INT TERM
  {
    printf 'PGPASSWORD=%s\n' "$(cat "$SECRETS_DIR/postgres_admin_password")"
    printf 'AIOPS_LEGACY_DB_OWNER=%s\n' "$legacy_user"
  } > "$finalize_credentials"
  chmod 0600 "$finalize_credentials"

  {
    printf '%s\n' '\getenv legacy_owner AIOPS_LEGACY_DB_OWNER'
    cat "$DEPLOY_DIR/init/004-finalize-legacy-role.sql"
  } | docker exec -i --env-file "$finalize_credentials" "$target_postgres" \
    psql --single-transaction --set=ON_ERROR_STOP=1 \
      --username aegisops_admin --dbname "$legacy_database"

  remove_legacy_containers "$compose_project" "$legacy_container_ids"
  mv -f "$STATE_FILE" "$FINALIZED_STATE_FILE"
  echo "Legacy PostgreSQL role disabled and preserved containers removed after target health verification."
  exit 0
fi

if [ -s "$STATE_FILE" ]; then
  compose_project="$(read_state_value AIOPS_LEGACY_COMPOSE_PROJECT)"
  migration_id="$(read_state_value AIOPS_LEGACY_MIGRATION_ID)"
  legacy_container_ids="$(read_state_value AIOPS_LEGACY_CONTAINER_IDS)"
  if [ -z "$compose_project" ] || [ -z "$migration_id" ] || [ -z "$legacy_container_ids" ]; then
    echo "Legacy migration state is incomplete; refusing to continue." >&2
    exit 1
  fi
  quiesce_legacy_containers "$compose_project" "$migration_id" "$legacy_container_ids"
  echo "Legacy migration is prepared and awaits target health finalization."
  exit 0
fi

is_legacy_postgres() {
  local container=$1
  [ "$(container_label "$container" com.docker.compose.service)" = "postgres" ] \
    && [ "$(container_env_value "$container" POSTGRES_USER)" != "aegisops_admin" ]
}

candidates=()
candidate_count=0
append_candidate() {
  local candidate=$1
  local index
  [ -n "$candidate" ] || return 0
  for ((index = 0; index < candidate_count; index++)); do
    [ "${candidates[$index]}" != "$candidate" ] || return 0
  done
  candidates[$candidate_count]="$candidate"
  candidate_count=$((candidate_count + 1))
}

if [ -n "$LEGACY_POSTGRES_CONTAINER" ]; then
  if ! container_exists "$LEGACY_POSTGRES_CONTAINER"; then
    exit 0
  fi
  append_candidate "$LEGACY_POSTGRES_CONTAINER"
else
  # The former full/infra Compose files pinned this name. Restrict discovery to
  # known AegisOps projects so an unrelated same-name container is never adopted.
  if container_exists aegisops-postgres; then
    fixed_project="$(container_label aegisops-postgres com.docker.compose.project)"
    case "$fixed_project" in
      deploy|infra)
        if is_legacy_postgres aegisops-postgres; then
          append_candidate aegisops-postgres
        fi
        ;;
      aegisops)
        if [ "$(container_env_value aegisops-postgres POSTGRES_USER)" != "aegisops_admin" ]; then
          echo "Container aegisops-postgres uses the target project but not the target admin role; refusing migration." >&2
          exit 1
        fi
        ;;
      *)
        echo "Container aegisops-postgres belongs to unexpected Compose project ${fixed_project:-<none>}; refusing migration." >&2
        exit 1
        ;;
    esac
  fi

  # The old Core file used name: aegisops-core, so Compose generated a name such
  # as aegisops-core-postgres-1 instead of the pinned full-stack name.
  while IFS= read -r candidate; do
    if container_exists "$candidate" && is_legacy_postgres "$candidate"; then
      append_candidate "$candidate"
    fi
  done < <(docker ps -a \
    --filter 'label=com.docker.compose.project=aegisops-core' \
    --filter 'label=com.docker.compose.service=postgres' \
    --format '{{.Names}}')
fi

if [ "$candidate_count" -eq 0 ]; then
  exit 0
fi
if [ "$candidate_count" -ne 1 ]; then
  echo "Multiple legacy PostgreSQL containers were found; set AIOPS_LEGACY_POSTGRES_CONTAINER explicitly: ${candidates[*]}" >&2
  exit 1
fi
LEGACY_POSTGRES_CONTAINER="${candidates[0]}"

legacy_user="$(container_env_value "$LEGACY_POSTGRES_CONTAINER" POSTGRES_USER)"
legacy_database="$(container_env_value "$LEGACY_POSTGRES_CONTAINER" POSTGRES_DB)"
legacy_user="${legacy_user:-aegisops}"
legacy_database="${legacy_database:-aegisops}"

if [ "$legacy_user" = "aegisops_admin" ]; then
  exit 0
fi

compose_service="$(container_label "$LEGACY_POSTGRES_CONTAINER" com.docker.compose.service)"
if [ "$compose_service" != "postgres" ]; then
  echo "Container $LEGACY_POSTGRES_CONTAINER is not a Compose PostgreSQL service; refusing migration." >&2
  exit 1
fi
compose_project="$(container_label "$LEGACY_POSTGRES_CONTAINER" com.docker.compose.project)"
if [ -z "$compose_project" ]; then
  echo "Container $LEGACY_POSTGRES_CONTAINER has no Compose project label; refusing migration." >&2
  exit 1
fi
if [ -n "$EXPECTED_LEGACY_PROJECT" ] && [ "$compose_project" != "$EXPECTED_LEGACY_PROJECT" ]; then
  echo "Container $LEGACY_POSTGRES_CONTAINER belongs to Compose project $compose_project, expected $EXPECTED_LEGACY_PROJECT; refusing migration." >&2
  exit 1
fi
if [ -z "$EXPECTED_LEGACY_PROJECT" ]; then
  case "$compose_project" in
    deploy|infra|aegisops-core) ;;
    *)
      echo "Compose project $compose_project is not a known legacy AegisOps topology; set AIOPS_LEGACY_COMPOSE_PROJECT explicitly after verification." >&2
      exit 1
      ;;
  esac
fi
if [ "$legacy_database" != "aegisops" ]; then
  echo "Legacy database is $legacy_database, but the target topology requires aegisops; refusing automatic migration." >&2
  exit 1
fi
if [ "$legacy_user" != "aegisops" ]; then
  echo "Legacy owner $legacy_user is not the supported aegisops role; refusing automatic migration." >&2
  exit 1
fi

legacy_volume="$(docker inspect \
  --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}' \
  "$LEGACY_POSTGRES_CONTAINER")"
if [ -z "$legacy_volume" ]; then
  echo "Legacy PostgreSQL does not use a named data volume; automatic migration is unavailable." >&2
  exit 1
fi

legacy_container_ids_array=()
legacy_container_id_count=0
append_legacy_container_id() {
  local candidate_id=$1
  local index
  [ -n "$candidate_id" ] || return 0
  for ((index = 0; index < legacy_container_id_count; index++)); do
    [ "${legacy_container_ids_array[$index]}" != "$candidate_id" ] || return 0
  done
  legacy_container_ids_array[$legacy_container_id_count]="$candidate_id"
  legacy_container_id_count=$((legacy_container_id_count + 1))
}
for service in postgres aiops-server aiops-worker aiops-agent aiops-runner; do
  while IFS= read -r candidate_id; do
    append_legacy_container_id "$candidate_id"
  done < <(docker ps -aq \
    --filter "label=com.docker.compose.project=$compose_project" \
    --filter "label=com.docker.compose.service=$service")
done
legacy_postgres_id="$(docker inspect --format '{{.Id}}' "$LEGACY_POSTGRES_CONTAINER")"
append_legacy_container_id "$legacy_postgres_id"
legacy_container_ids="$(IFS=,; printf '%s' "${legacy_container_ids_array[*]}")"

for required_file in \
  "$ENV_FILE" \
  "$SECRETS_DIR/postgres_admin_password" \
  "$SECRETS_DIR/app_db_password" \
  "$SECRETS_DIR/runner_db_password" \
  "$DEPLOY_DIR/init/001-create-roles.sql" \
  "$DEPLOY_DIR/init/003-migrate-legacy-owner.sql"; do
  if [ ! -s "$required_file" ]; then
    echo "Legacy migration prerequisite is missing or empty: $required_file" >&2
    exit 1
  fi
done

if [ "$(docker inspect --format '{{.State.Running}}' "$LEGACY_POSTGRES_CONTAINER")" != "true" ]; then
  docker start "$LEGACY_POSTGRES_CONTAINER" >/dev/null
fi

backup_dir="$RUNTIME_DIR/backups"
mkdir -p "$backup_dir"
chmod 0700 "$backup_dir"
backup_temp="$(mktemp "$backup_dir/.pre-mtls-$(date -u +%Y%m%dT%H%M%SZ).XXXXXX")"
if ! docker exec "$LEGACY_POSTGRES_CONTAINER" \
  pg_dump --format=custom --username "$legacy_user" --dbname "$legacy_database" \
  > "$backup_temp"; then
  rm -f -- "$backup_temp"
  echo "Legacy PostgreSQL backup failed; refusing migration." >&2
  exit 1
fi
if [ ! -s "$backup_temp" ]; then
  rm -f -- "$backup_temp"
  echo "Legacy PostgreSQL backup is empty; refusing migration." >&2
  exit 1
fi
backup_name="${backup_temp##*/}"
backup_file="$backup_dir/${backup_name#.}.dump"
mv "$backup_temp" "$backup_file"
chmod 0600 "$backup_file"

credentials_file="$(mktemp "$RUNTIME_DIR/.legacy-db.XXXXXX")"
cleanup() {
  rm -f -- "$credentials_file"
}
trap cleanup EXIT HUP INT TERM
{
  printf 'AIOPS_POSTGRES_ADMIN_PASSWORD=%s\n' "$(cat "$SECRETS_DIR/postgres_admin_password")"
  printf 'AIOPS_APP_DB_PASSWORD=%s\n' "$(cat "$SECRETS_DIR/app_db_password")"
  printf 'AIOPS_RUNNER_DB_PASSWORD=%s\n' "$(cat "$SECRETS_DIR/runner_db_password")"
  printf 'AIOPS_LEGACY_DB_OWNER=%s\n' "$legacy_user"
  printf 'AIOPS_LEGACY_DB_NAME=%s\n' "$legacy_database"
} > "$credentials_file"
chmod 0600 "$credentials_file"

{
  printf '%s\n' \
    '\getenv admin_password AIOPS_POSTGRES_ADMIN_PASSWORD' \
    '\getenv app_password AIOPS_APP_DB_PASSWORD' \
    '\getenv runner_password AIOPS_RUNNER_DB_PASSWORD' \
    '\getenv legacy_owner AIOPS_LEGACY_DB_OWNER' \
    '\getenv database_name AIOPS_LEGACY_DB_NAME'
  cat "$DEPLOY_DIR/init/001-create-roles.sql"
  cat "$DEPLOY_DIR/init/003-migrate-legacy-owner.sql"
} | docker exec -i --env-file "$credentials_file" "$LEGACY_POSTGRES_CONTAINER" \
  psql --single-transaction --set=ON_ERROR_STOP=1 \
    --username "$legacy_user" --dbname "$legacy_database"

env_temp="$(mktemp "$RUNTIME_DIR/.env.XXXXXX")"
awk -F= '$1 != "AIOPS_POSTGRES_VOLUME_NAME"' "$ENV_FILE" > "$env_temp"
printf 'AIOPS_POSTGRES_VOLUME_NAME=%s\n' "$legacy_volume" >> "$env_temp"
chmod 0600 "$env_temp"
mv "$env_temp" "$ENV_FILE"

migration_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
state_temp="$(mktemp "$RUNTIME_DIR/.legacy-migration.XXXXXX")"
{
  printf 'AIOPS_LEGACY_DB_OWNER=%s\n' "$legacy_user"
  printf 'AIOPS_LEGACY_DB_NAME=%s\n' "$legacy_database"
  printf 'AIOPS_LEGACY_COMPOSE_PROJECT=%s\n' "$compose_project"
  printf 'AIOPS_LEGACY_POSTGRES_VOLUME=%s\n' "$legacy_volume"
  printf 'AIOPS_LEGACY_CONTAINER_IDS=%s\n' "$legacy_container_ids"
  printf 'AIOPS_LEGACY_MIGRATION_ID=%s\n' "$migration_id"
  printf 'AIOPS_LEGACY_BACKUP_FILE=%s\n' "$backup_file"
} > "$state_temp"
chmod 0600 "$state_temp"
mv "$state_temp" "$STATE_FILE"

quiesce_legacy_containers "$compose_project" "$migration_id" "$legacy_container_ids"

echo "Legacy PostgreSQL prepared in place; backup preserved at $backup_file. Finalize only after the target stack is healthy."

#!/usr/bin/env bash
set -Eeuo pipefail

: "${AIOPS_APP_IMAGE:?AIOPS_APP_IMAGE is required}"
: "${AIOPS_AGENT_IMAGE:?AIOPS_AGENT_IMAGE is required}"
: "${AIOPS_RUNNER_IMAGE:?AIOPS_RUNNER_IMAGE is required}"

validate_digest_image() {
  local variable_name=$1
  local image_reference="${!variable_name}"
  local digest
  case "$image_reference" in
    ?*@sha256:*) ;;
    *)
      echo "$variable_name must be a digest-pinned image reference." >&2
      exit 1
      ;;
  esac
  digest="${image_reference##*@sha256:}"
  if [[ ! "$digest" =~ ^[0-9a-f]{64}$ ]]; then
    echo "$variable_name must be a digest-pinned image reference." >&2
    exit 1
  fi
}

for image_variable in AIOPS_APP_IMAGE AIOPS_AGENT_IMAGE AIOPS_RUNNER_IMAGE; do
  validate_digest_image "$image_variable"
done
export AIOPS_APP_IMAGE AIOPS_AGENT_IMAGE AIOPS_RUNNER_IMAGE

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/deploy/docker-compose.core.yml}"
INSTALL_SCRIPT="${INSTALL_SCRIPT:-$APP_DIR/deploy/install.sh}"
MTLS_PROBE_SCRIPT="${MTLS_PROBE_SCRIPT:-$APP_DIR/deploy/scripts/verify-internal-mtls.py}"
RUNTIME_DIR="${AIOPS_RUNTIME_DIR:-$APP_DIR/deploy/runtime}"
ENV_FILE="$RUNTIME_DIR/.env"
DEPLOY_MODE="${AIOPS_DEPLOY_MODE:-diagnostic}"
LEGACY_MIGRATION_SCRIPT="${LEGACY_MIGRATION_SCRIPT:-$APP_DIR/deploy/scripts/migrate-legacy-compose.sh}"
ZABBIX_NETWORK_SCRIPT="${ZABBIX_NETWORK_SCRIPT:-$SCRIPT_DIR/ensure-zabbix-api-network.sh}"
CORE_BACKUP_SCRIPT="${CORE_BACKUP_SCRIPT:-$SCRIPT_DIR/backup-core.sh}"
CORE_RESTORE_SCRIPT="${CORE_RESTORE_SCRIPT:-$SCRIPT_DIR/restore-core.sh}"
CORE_PREDEPLOY_BACKUP_DIR="${CORE_PREDEPLOY_BACKUP_DIR:-}"
CORE_BACKUP_ROOT="${CORE_BACKUP_ROOT:-$APP_DIR/deploy/backups}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is not available: $1" >&2
    exit 1
  }
}

for command_name in bash docker python3 sha256sum; do
  require_command "$command_name"
done

for required_file in \
  "$COMPOSE_FILE" \
  "$INSTALL_SCRIPT" \
  "$MTLS_PROBE_SCRIPT" \
  "$ZABBIX_NETWORK_SCRIPT" \
  "$CORE_BACKUP_SCRIPT" \
  "$CORE_RESTORE_SCRIPT" \
  "$LEGACY_MIGRATION_SCRIPT"; do
  if [ ! -f "$required_file" ]; then
    echo "Required deployment file is missing: $required_file" >&2
    exit 1
  fi
done

case "$DEPLOY_MODE" in
  core|diagnostic|automation) ;;
  *)
    echo "Unsupported AIOPS_DEPLOY_MODE: $DEPLOY_MODE" >&2
    exit 2
    ;;
esac

compose_args=(--env-file "$ENV_FILE" -f "$COMPOSE_FILE")
case "$DEPLOY_MODE" in
  diagnostic)
    compose_args+=(--profile ai)
    ;;
  automation)
    compose_args+=(--profile ai --profile automation)
    ;;
esac

compose() {
  docker compose "${compose_args[@]}" "$@"
}

run_core_backup() {
  APP_DIR="$APP_DIR" \
    COMPOSE_FILE="$COMPOSE_FILE" \
    CORE_BACKUP_ROOT="$CORE_BACKUP_ROOT" \
    bash "$CORE_BACKUP_SCRIPT" "$@"
}

bash "$ZABBIX_NETWORK_SCRIPT"

core_state="$(run_core_backup --probe-only)"
core_backup_dir=""
case "$core_state" in
  running)
    if [ -n "$CORE_PREDEPLOY_BACKUP_DIR" ]; then
      echo "==> Validating staged Core database backup"
      core_backup_dir="$(run_core_backup --validate "$CORE_PREDEPLOY_BACKUP_DIR")"
    else
      echo "==> Backing up Core PostgreSQL before deployment"
      core_backup_dir="$(run_core_backup)"
      core_backup_dir="$(run_core_backup --validate "$core_backup_dir")"
    fi
    echo "Validated Core backup: $core_backup_dir"
    ;;
  empty)
    if [ -n "$CORE_PREDEPLOY_BACKUP_DIR" ]; then
      echo "A Core pre-deployment backup was supplied but no existing state was found." >&2
      exit 1
    fi
    ;;
  legacy)
    if [ -n "$CORE_PREDEPLOY_BACKUP_DIR" ]; then
      echo "A current-Core backup cannot be applied to a legacy deployment." >&2
      exit 1
    fi
    echo "==> Supported legacy PostgreSQL detected; migration will create its own backup before modification"
    ;;
  *)
    echo "Unexpected Core state probe result: $core_state" >&2
    exit 1
    ;;
esac

if [ ! -f "$ENV_FILE" ] || [ ! -d "$RUNTIME_DIR/secrets" ]; then
  echo "==> Initializing persistent deployment credentials"
  bash "$INSTALL_SCRIPT" \
    --mode "$DEPLOY_MODE" \
    --runtime-dir "$RUNTIME_DIR" \
    --no-start
else
  echo "==> Reusing deployment credentials from $RUNTIME_DIR"
fi

AIOPS_RUNTIME_DIR="$RUNTIME_DIR" bash "$LEGACY_MIGRATION_SCRIPT"

required_runtime_files=(
  .env
  secrets/postgres_admin_password
  secrets/app_db_password
  secrets/runner_db_password
  secrets/app.crt
  secrets/app.key
  secrets/agent.crt
  secrets/agent.key
  secrets/control_plane_ca.crt
  secrets/agent_ca.crt
  secrets/grant-private.pem
  secrets/grant-public.pem
)
for relative_path in "${required_runtime_files[@]}"; do
  if [ ! -f "$RUNTIME_DIR/$relative_path" ]; then
    echo "Deployment runtime is incomplete: $RUNTIME_DIR/$relative_path" >&2
    exit 1
  fi
done

echo "==> Validating deployment descriptor"
compose config --quiet

echo "==> Pulling digest-pinned release images"
compose pull --policy always

echo "==> Starting AegisOps $DEPLOY_MODE deployment"
compose up -d --wait --remove-orphans --no-build

if [ "$DEPLOY_MODE" != "core" ]; then
  echo "==> Verifying Agent to App internal mTLS"
  compose exec -T aiops-agent \
    python - \
      --host aegisops-app \
      --port 8443 \
      --server-name aegisops-app \
      --path /actuator/health \
      --cert /run/secrets/agent_tls_cert \
      --key /run/secrets/agent_tls_key \
      --ca /run/secrets/control_plane_ca_cert \
      --expected-spiffe spiffe://aegisops.local/service/aegisops-app \
      < "$MTLS_PROBE_SCRIPT"
fi

compose ps
if [ -n "$core_backup_dir" ]; then
  : > "$core_backup_dir/images.after.tsv"
  while IFS=$'\t' read -r container_name service_name _; do
    [ -n "$container_name" ] || continue
    if docker inspect "$container_name" >/dev/null 2>&1; then
      printf '%s\t%s\t%s\t%s\n' \
        "$container_name" \
        "$service_name" \
        "$(docker inspect --format '{{.Config.Image}}' "$container_name")" \
        "$(docker inspect --format '{{.Image}}' "$container_name")" \
        >> "$core_backup_dir/images.after.tsv"
    fi
  done < "$core_backup_dir/images.before.tsv"
  (
    cd "$core_backup_dir"
    sha256sum images.after.tsv >> SHA256SUMS
  )
  echo "Rollback command: bash $CORE_RESTORE_SCRIPT --backup-dir $core_backup_dir --execute --confirm RESTORE-CORE"
fi
AIOPS_RUNTIME_DIR="$RUNTIME_DIR" \
  AIOPS_TARGET_STACK_HEALTHY=true \
  bash "$LEGACY_MIGRATION_SCRIPT" --finalize
docker image prune -f --filter 'until=168h' >/dev/null 2>&1 || true
echo "==> Deployment succeeded (${AIOPS_APP_IMAGE##*@})"

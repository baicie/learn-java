#!/usr/bin/env bash
set -Eeuo pipefail

: "${IMAGE_PREFIX:?IMAGE_PREFIX is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"

APP_DIR="${APP_DIR:-$HOME/workspace/aegisops}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/deploy/docker-compose.core.yml}"
INSTALL_SCRIPT="${INSTALL_SCRIPT:-$APP_DIR/deploy/install.sh}"
MTLS_PROBE_SCRIPT="${MTLS_PROBE_SCRIPT:-$APP_DIR/deploy/scripts/verify-internal-mtls.py}"
RUNTIME_DIR="${AIOPS_RUNTIME_DIR:-$APP_DIR/deploy/runtime}"
ENV_FILE="$RUNTIME_DIR/.env"
DEPLOY_MODE="${AIOPS_DEPLOY_MODE:-diagnostic}"
LEGACY_MIGRATION_SCRIPT="${LEGACY_MIGRATION_SCRIPT:-$APP_DIR/deploy/scripts/migrate-legacy-compose.sh}"

export AIOPS_APP_IMAGE="${IMAGE_PREFIX}:${IMAGE_TAG}-app"
export AIOPS_AGENT_IMAGE="${IMAGE_PREFIX}:${IMAGE_TAG}-agent"
export AIOPS_RUNNER_IMAGE="${IMAGE_PREFIX}:${IMAGE_TAG}-runner"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is not available: $1" >&2
    exit 1
  }
}

for command_name in bash docker python3; do
  require_command "$command_name"
done

for required_file in \
  "$COMPOSE_FILE" \
  "$INSTALL_SCRIPT" \
  "$MTLS_PROBE_SCRIPT" \
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

echo "==> Pulling immutable release images ($IMAGE_TAG)"
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
AIOPS_RUNTIME_DIR="$RUNTIME_DIR" \
  AIOPS_TARGET_STACK_HEALTHY=true \
  bash "$LEGACY_MIGRATION_SCRIPT" --finalize
docker image prune -f --filter 'until=168h' >/dev/null 2>&1 || true
echo "==> Deployment succeeded ($IMAGE_TAG)"

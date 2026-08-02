#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.core.yml"
RUNTIME_DIR="$SCRIPT_DIR/runtime"
MODE="diagnostic"
START=1
FORCE=0

usage() {
  cat <<'EOF'
Usage: deploy/install.sh [options]

Options:
  --mode core|diagnostic|automation  Deployment mode (default: diagnostic)
  --runtime-dir PATH                 Generated state directory (default: deploy/runtime)
  --no-start                         Generate material without starting containers
  --force                            Rotate TLS/task material; preserve database passwords
  --help                             Show this help
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mode)
      [ "$#" -ge 2 ] || { echo "--mode requires a value" >&2; exit 2; }
      MODE="$2"
      shift 2
      ;;
    --runtime-dir)
      [ "$#" -ge 2 ] || { echo "--runtime-dir requires a value" >&2; exit 2; }
      RUNTIME_DIR="$2"
      shift 2
      ;;
    --no-start)
      START=0
      shift
      ;;
    --force)
      FORCE=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

case "$MODE" in
  core|diagnostic|automation) ;;
  *)
    echo "Unsupported mode: $MODE" >&2
    exit 2
    ;;
esac

require_command() {
  local name=$1
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Required command is not available: $name" >&2
    exit 1
  fi
}

require_command openssl
require_command mktemp
if [ "$START" = "1" ]; then
  require_command docker
fi

OPENSSL_EXECUTABLE=""
for candidate in \
  "${OPENSSL_BIN:-}" \
  /opt/homebrew/opt/openssl@3/bin/openssl \
  /usr/local/opt/openssl@3/bin/openssl \
  "$(command -v openssl)"; do
  if [ -n "$candidate" ] \
    && [ -x "$candidate" ] \
    && "$candidate" version 2>/dev/null | grep -Eq '^OpenSSL (1\.1\.1|[3-9])'; then
    OPENSSL_EXECUTABLE="$candidate"
    break
  fi
done
if [ -z "$OPENSSL_EXECUTABLE" ]; then
  echo "OpenSSL 1.1.1+ with Ed25519 support is required." >&2
  exit 1
fi

openssl() {
  "$OPENSSL_EXECUTABLE" "$@"
}

mkdir -p "$RUNTIME_DIR"
chmod 0700 "$RUNTIME_DIR"
SECRETS_DIR="$RUNTIME_DIR/secrets"
ENV_FILE="$RUNTIME_DIR/.env"

REQUIRED_SECURITY_FILES=(
  postgres_admin_password
  app_db_password
  runner_db_password
  control_plane_ca.crt
  control_plane_ca.key
  agent_ca.crt
  agent_ca.key
  app.crt
  app.key
  agent.crt
  agent.key
  grant-private.pem
  grant-public.pem
)
DATABASE_PASSWORD_FILES=(postgres_admin_password app_db_password runner_db_password)
COMPOSE_SECRET_FILES=(
  postgres_admin_password
  app_db_password
  runner_db_password
  control_plane_ca.crt
  agent_ca.crt
  app.crt
  app.key
  agent.crt
  agent.key
  grant-private.pem
  grant-public.pem
  grant-previous-public.pem
)

require_files() {
  local directory=$1
  shift
  local name
  for name in "$@"; do
    if [ ! -s "$directory/$name" ]; then
      echo "Required deployment material is missing or empty: $directory/$name" >&2
      exit 1
    fi
  done
}

read_env_value() {
  local key=$1
  awk -F= -v expected="$key" '$1 == expected {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE"
}

CURRENT_GRANT_KEY_ID="${AIOPS_TASK_GRANT_KEY_ID:-task-grant-v1}"
PREVIOUS_GRANT_KEY_ID="${AIOPS_TASK_GRANT_PREVIOUS_KEY_ID:-}"
POSTGRES_VOLUME_NAME="${AIOPS_POSTGRES_VOLUME_NAME:-aegisops_postgres_data}"
if [ -s "$ENV_FILE" ]; then
  stored_value="$(read_env_value AIOPS_TASK_GRANT_KEY_ID)"
  CURRENT_GRANT_KEY_ID="${stored_value:-$CURRENT_GRANT_KEY_ID}"
  stored_value="$(read_env_value AIOPS_TASK_GRANT_PREVIOUS_KEY_ID)"
  PREVIOUS_GRANT_KEY_ID="${stored_value:-$PREVIOUS_GRANT_KEY_ID}"
  stored_value="$(read_env_value AIOPS_POSTGRES_VOLUME_NAME)"
  POSTGRES_VOLUME_NAME="${stored_value:-$POSTGRES_VOLUME_NAME}"
fi

REUSE=0
PRESERVED_SECRETS_DIR=""
TEMP_DIR=""
cleanup() {
  if [ -n "$TEMP_DIR" ] && [ -d "$TEMP_DIR" ]; then
    rm -rf -- "$TEMP_DIR"
  fi
}
trap cleanup EXIT

random_hex() {
  openssl rand -hex 32
}

if [ -d "$SECRETS_DIR" ]; then
  if [ "$FORCE" = "1" ]; then
    require_files "$SECRETS_DIR" "${DATABASE_PASSWORD_FILES[@]}" grant-public.pem
    BACKUP_DIR="$RUNTIME_DIR/secrets.backup.$(date -u +%Y%m%dT%H%M%SZ).$$"
    mv "$SECRETS_DIR" "$BACKUP_DIR"
    PRESERVED_SECRETS_DIR="$BACKUP_DIR"
    echo "Previous security material preserved at $BACKUP_DIR"
  else
    require_files "$SECRETS_DIR" "${REQUIRED_SECURITY_FILES[@]}"
    if [ ! -s "$ENV_FILE" ]; then
      echo "Deployment environment file is missing or empty: $ENV_FILE" >&2
      exit 1
    fi
    JWT_SECRET="$(read_env_value AIOPS_JWT_SECRET)"
    WEBHOOK_SECRET="$(read_env_value AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN)"
    if [ -z "$JWT_SECRET" ] || [ -z "$WEBHOOK_SECRET" ]; then
      echo "Deployment environment file is missing generated secrets: $ENV_FILE" >&2
      exit 1
    fi
    REUSE=1
    echo "Reusing deployment material at $RUNTIME_DIR"
  fi
elif [ -e "$SECRETS_DIR" ]; then
  echo "Deployment secrets path exists but is not a directory: $SECRETS_DIR" >&2
  exit 1
fi

if [ "$REUSE" = "0" ]; then
  TEMP_DIR="$(mktemp -d "$RUNTIME_DIR/.install.XXXXXX")"
  GENERATED_SECRETS="$TEMP_DIR/secrets"
  mkdir -p "$GENERATED_SECRETS"

  if [ -n "$PRESERVED_SECRETS_DIR" ]; then
    for name in "${DATABASE_PASSWORD_FILES[@]}"; do
      cp "$PRESERVED_SECRETS_DIR/$name" "$GENERATED_SECRETS/$name"
    done
  else
    random_hex > "$GENERATED_SECRETS/postgres_admin_password"
    random_hex > "$GENERATED_SECRETS/app_db_password"
    random_hex > "$GENERATED_SECRETS/runner_db_password"
  fi
  JWT_SECRET="$(random_hex)"
  WEBHOOK_SECRET="$(random_hex)"

cat > "$TEMP_DIR/app-leaf.cnf" <<'EOF'
[v3_leaf]
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = @alt_names
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer

[alt_names]
DNS.1 = aegisops-app
URI.1 = spiffe://aegisops.local/service/aegisops-app
EOF

cat > "$TEMP_DIR/agent-leaf.cnf" <<'EOF'
[v3_leaf]
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = @alt_names
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer

[alt_names]
DNS.1 = aiops-agent
URI.1 = spiffe://aegisops.local/service/aiops-agent
EOF

generate_role_ca() {
  local name=$1
  local common_name=$2
  openssl req -x509 -newkey rsa:4096 -nodes \
    -keyout "$GENERATED_SECRETS/${name}_ca.key" \
    -out "$GENERATED_SECRETS/${name}_ca.crt" \
    -days 1825 \
    -subj "/CN=${common_name}" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" >/dev/null 2>&1
}

generate_role_ca control_plane "AegisOps Control Plane CA"
generate_role_ca agent "AegisOps Agent CA"

generate_leaf() {
  local name=$1
  local common_name=$2
  local ca_name=$3
  local serial=$4
  openssl req -new -newkey rsa:3072 -nodes \
    -keyout "$GENERATED_SECRETS/${name}.key" \
    -out "$TEMP_DIR/${name}.csr" \
    -subj "/CN=${common_name}" >/dev/null 2>&1
  openssl x509 -req \
    -in "$TEMP_DIR/${name}.csr" \
    -CA "$GENERATED_SECRETS/${ca_name}_ca.crt" \
    -CAkey "$GENERATED_SECRETS/${ca_name}_ca.key" \
    -set_serial "$serial" \
    -out "$GENERATED_SECRETS/${name}.crt" \
    -days 825 \
    -extfile "$TEMP_DIR/${name}-leaf.cnf" \
    -extensions v3_leaf >/dev/null 2>&1
}

generate_leaf app aegisops-app control_plane 2001
generate_leaf agent aiops-agent agent 2002

openssl genpkey -algorithm ED25519 \
  -out "$GENERATED_SECRETS/grant-private.pem" >/dev/null 2>&1
  openssl pkey \
  -in "$GENERATED_SECRETS/grant-private.pem" \
    -pubout \
    -out "$GENERATED_SECRETS/grant-public.pem" >/dev/null 2>&1

  if [ -n "$PRESERVED_SECRETS_DIR" ]; then
    PREVIOUS_GRANT_KEY_ID="$CURRENT_GRANT_KEY_ID"
    CURRENT_GRANT_KEY_ID="task-grant-$(date -u +%Y%m%dT%H%M%SZ)-$$"
    cp "$PRESERVED_SECRETS_DIR/grant-public.pem" \
      "$GENERATED_SECRETS/grant-previous-public.pem"
  else
    PREVIOUS_GRANT_KEY_ID=""
    : > "$GENERATED_SECRETS/grant-previous-public.pem"
  fi

chmod 0600 "$GENERATED_SECRETS"/*
chmod 0644 \
  "$GENERATED_SECRETS/control_plane_ca.crt" \
  "$GENERATED_SECRETS/agent_ca.crt" \
  "$GENERATED_SECRETS/app.crt" \
  "$GENERATED_SECRETS/agent.crt" \
  "$GENERATED_SECRETS/grant-public.pem" \
  "$GENERATED_SECRETS/grant-previous-public.pem"

  mv "$GENERATED_SECRETS" "$SECRETS_DIR"
  chmod 0700 "$RUNTIME_DIR" "$SECRETS_DIR"
  echo "Generated deployment material at $RUNTIME_DIR"
fi

if [ ! -e "$SECRETS_DIR/grant-previous-public.pem" ]; then
  : > "$SECRETS_DIR/grant-previous-public.pem"
  chmod 0644 "$SECRETS_DIR/grant-previous-public.pem"
fi
if [ -n "$PREVIOUS_GRANT_KEY_ID" ] \
  && [ ! -s "$SECRETS_DIR/grant-previous-public.pem" ]; then
  echo "Previous Grant key id is configured but its public key is missing." >&2
  exit 1
fi

# Docker Compose bind-mounts file-backed secrets and cannot remap their uid/gid/mode.
# Keep canonical material private, then expose only the mounted allowlist behind a 0700 directory.
COMPOSE_SECRETS_DIR="$RUNTIME_DIR/compose-secrets"
if [ -L "$COMPOSE_SECRETS_DIR" ] \
  || { [ -e "$COMPOSE_SECRETS_DIR" ] && [ ! -d "$COMPOSE_SECRETS_DIR" ]; }; then
  echo "Compose secrets path exists but is not a regular directory: $COMPOSE_SECRETS_DIR" >&2
  exit 1
fi
mkdir -p "$COMPOSE_SECRETS_DIR"
chmod 0700 "$COMPOSE_SECRETS_DIR"
for name in "${COMPOSE_SECRET_FILES[@]}"; do
  if [ ! -e "$SECRETS_DIR/$name" ]; then
    echo "Required Compose secret is missing: $SECRETS_DIR/$name" >&2
    exit 1
  fi
  cp "$SECRETS_DIR/$name" "$COMPOSE_SECRETS_DIR/$name"
  chmod 0644 "$COMPOSE_SECRETS_DIR/$name"
done

case "$MODE" in
  core)
    AGENT_ENABLED=false
    ;;
  diagnostic|automation)
    AGENT_ENABLED=true
    ;;
esac

PREVIOUS_GRANT_PUBLIC_KEY_FILE=""
if [ -n "$PREVIOUS_GRANT_KEY_ID" ]; then
  PREVIOUS_GRANT_PUBLIC_KEY_FILE=/run/secrets/task_grant_previous_public_key
fi

ENV_TEMP="$(mktemp "$RUNTIME_DIR/.env.XXXXXX")"
cat > "$ENV_TEMP" <<EOF
AIOPS_SECRETS_DIR=$COMPOSE_SECRETS_DIR
AIOPS_JWT_SECRET=$JWT_SECRET
AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN=$WEBHOOK_SECRET
AIOPS_AGENT_ENABLED=$AGENT_ENABLED
AIOPS_INTERNAL_AGENT_API_ENABLED=$AGENT_ENABLED
AIOPS_POSTGRES_VOLUME_NAME=$POSTGRES_VOLUME_NAME
AIOPS_TASK_GRANT_KEY_ID=$CURRENT_GRANT_KEY_ID
AIOPS_TASK_GRANT_PREVIOUS_KEY_ID=$PREVIOUS_GRANT_KEY_ID
AIOPS_TASK_GRANT_PREVIOUS_PUBLIC_KEY_FILE=$PREVIOUS_GRANT_PUBLIC_KEY_FILE
EOF
chmod 0600 "$ENV_TEMP"
mv "$ENV_TEMP" "$ENV_FILE"
chmod 0700 "$RUNTIME_DIR" "$SECRETS_DIR"

if [ "$START" = "0" ]; then
  exit 0
fi

AIOPS_RUNTIME_DIR="$RUNTIME_DIR" bash "$SCRIPT_DIR/scripts/migrate-legacy-compose.sh"

compose_args=(--env-file "$ENV_FILE" -f "$COMPOSE_FILE")
case "$MODE" in
  diagnostic)
    compose_args+=(--profile ai)
    ;;
  automation)
    compose_args+=(--profile ai --profile automation)
    ;;
esac

docker compose "${compose_args[@]}" up -d --build --wait
docker compose "${compose_args[@]}" ps
AIOPS_RUNTIME_DIR="$RUNTIME_DIR" \
  AIOPS_TARGET_STACK_HEALTHY=true \
  bash "$SCRIPT_DIR/scripts/migrate-legacy-compose.sh" --finalize

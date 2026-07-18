#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY_SCRIPT="$ROOT_DIR/deploy/scripts/deploy-zabbix.sh"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.zabbix.yml"
TMP_ROOT="$(mktemp -d)"
MOCK_BIN="$TMP_ROOT/bin"
ENV_FILE="$TMP_ROOT/.env.zabbix"
DOCKER_LOG="$TMP_ROOT/docker.log"
OPENSSL_LOG="$TMP_ROOT/openssl.log"

cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

mkdir -p "$MOCK_BIN"

cat > "$MOCK_BIN/docker" <<'MOCK_DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$MOCK_DOCKER_LOG"
MOCK_DOCKER

cat > "$MOCK_BIN/openssl" <<'MOCK_OPENSSL'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$MOCK_OPENSSL_LOG"
printf 'fixed-test-password\n'
MOCK_OPENSSL

chmod +x "$MOCK_BIN/docker" "$MOCK_BIN/openssl"

run_deploy() {
  PATH="$MOCK_BIN:$PATH" \
  MOCK_DOCKER_LOG="$DOCKER_LOG" \
  MOCK_OPENSSL_LOG="$OPENSSL_LOG" \
  COMPOSE_FILE="$COMPOSE_FILE" \
  ZABBIX_ENV_FILE="$ENV_FILE" \
    bash "$DEPLOY_SCRIPT"
}

run_deploy
grep -Fq 'ZABBIX_DB_PASSWORD=fixed-test-password' "$ENV_FILE"
grep -Fq 'ZABBIX_WEB_PORT=8083' "$ENV_FILE"
[ "$(LC_ALL=C ls -ld "$ENV_FILE" | cut -c 1-10)" = "-rw-------" ]
cp "$ENV_FILE" "$TMP_ROOT/env.before"

run_deploy
cmp -s "$ENV_FILE" "$TMP_ROOT/env.before"
[ "$(wc -l < "$OPENSSL_LOG" | tr -d ' ')" = "1" ]
grep -Fq "compose --env-file $ENV_FILE -f $COMPOSE_FILE config --quiet" "$DOCKER_LOG"
grep -Fq "compose --env-file $ENV_FILE -f $COMPOSE_FILE pull" "$DOCKER_LOG"
grep -Fq "compose --env-file $ENV_FILE -f $COMPOSE_FILE up -d --remove-orphans --wait --wait-timeout 300" "$DOCKER_LOG"

echo "Zabbix deployment simulation passed."

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
NETWORK_STATE="$TMP_ROOT/zabbix-api-network"
NETWORK_MEMBERS="$TMP_ROOT/zabbix-api-network-members"
DEPLOY_OUTPUT="$TMP_ROOT/deploy-output.log"

cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

mkdir -p "$MOCK_BIN"

cat > "$MOCK_BIN/docker" <<'MOCK_DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$MOCK_DOCKER_LOG"
if [ "$1 ${2:-}" = "network inspect" ]; then
  [ -f "$MOCK_NETWORK_STATE" ] || exit 1
  case "$*" in
    *'range .Containers'*) cat "$MOCK_NETWORK_MEMBERS" ;;
    *) cat "$MOCK_NETWORK_STATE" ;;
  esac
elif [ "$1 ${2:-}" = "network create" ]; then
  printf 'bridge true\n' > "$MOCK_NETWORK_STATE"
elif [ "$1" = "inspect" ]; then
  exit 1
elif [ "$1 ${2:-}" = "volume inspect" ]; then
  exit 1
elif [ "$1 ${2:-}" = "volume ls" ]; then
  exit 0
fi
case "$*" in
  *" port zabbix-web 8080") printf '127.0.0.9:18083\n' ;;
  *" port zabbix-server 10051") printf '127.0.0.9:11051\n' ;;
esac
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
  MOCK_NETWORK_MEMBERS="$NETWORK_MEMBERS" \
  MOCK_NETWORK_STATE="$NETWORK_STATE" \
  MOCK_OPENSSL_LOG="$OPENSSL_LOG" \
  COMPOSE_FILE="$COMPOSE_FILE" \
  ZABBIX_ENV_FILE="$ENV_FILE" \
    bash "$DEPLOY_SCRIPT"
}

printf '%s\n' aegisops-app aegisops-zabbix-web > "$NETWORK_MEMBERS"
run_deploy >> "$DEPLOY_OUTPUT"
grep -Fq 'ZABBIX_DB_PASSWORD=fixed-test-password' "$ENV_FILE"
grep -Fq 'ZABBIX_BIND_ADDRESS=127.0.0.1' "$ENV_FILE"
grep -Fq 'ZABBIX_WEB_PORT=8083' "$ENV_FILE"
grep -Fq 'ZABBIX_SERVER_PORT=10051' "$ENV_FILE"
[ "$(LC_ALL=C ls -ld "$ENV_FILE" | cut -c 1-10)" = "-rw-------" ]
cp "$ENV_FILE" "$TMP_ROOT/env.before"

run_deploy >> "$DEPLOY_OUTPUT"
cmp -s "$ENV_FILE" "$TMP_ROOT/env.before"
[ "$(wc -l < "$OPENSSL_LOG" | tr -d ' ')" = "1" ]
[ "$(grep -Fc 'network inspect --format {{.Driver}} {{.Internal}} aegisops-zabbix-api' "$DOCKER_LOG")" = "3" ]
[ "$(grep -Fc 'network create --driver bridge --internal aegisops-zabbix-api' "$DOCKER_LOG")" = "1" ]
grep -Fq "compose --env-file $ENV_FILE -f $COMPOSE_FILE config --quiet" "$DOCKER_LOG"
grep -Fq "compose --env-file $ENV_FILE -f $COMPOSE_FILE pull" "$DOCKER_LOG"
grep -Fq "compose --env-file $ENV_FILE -f $COMPOSE_FILE up -d --remove-orphans --wait --wait-timeout 300" "$DOCKER_LOG"
grep -Fq 'Zabbix Web is available on 127.0.0.9:18083.' "$DEPLOY_OUTPUT"
grep -Fq 'Zabbix Server is available on 127.0.0.9:11051.' "$DEPLOY_OUTPUT"

: > "$DOCKER_LOG"
printf '%s\n' aegisops-app aegisops-zabbix-web unexpected-member > "$NETWORK_MEMBERS"
if run_deploy > "$TMP_ROOT/member-drift-output.log" 2>&1; then
  echo "Zabbix deployment must reject unexpected API network members." >&2
  exit 1
fi
if grep -Eq 'compose .* (pull|up)( |$)' "$DOCKER_LOG"; then
  echo "Zabbix deployment changed production state before rejecting member drift." >&2
  exit 1
fi
grep -Fq 'Unexpected aegisops-zabbix-api member: unexpected-member' \
  "$TMP_ROOT/member-drift-output.log"

echo "Zabbix deployment simulation passed."

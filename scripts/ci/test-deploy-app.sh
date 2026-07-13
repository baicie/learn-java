#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TMP_ROOT="$(mktemp -d)"
MOCK_BIN="$TMP_ROOT/bin"
MOCK_DOCKER_LOG="$TMP_ROOT/docker.log"

cleanup() {
  find "$TMP_ROOT" -depth -mindepth 1 -delete 2>/dev/null || true
  rmdir "$TMP_ROOT" 2>/dev/null || true
}
trap cleanup EXIT

mkdir -p "$MOCK_BIN" "$TMP_ROOT/app/deploy" "$TMP_ROOT/home" "$TMP_ROOT/tmp"
: >"$MOCK_DOCKER_LOG"
printf 'services: {}\n' >"$TMP_ROOT/app/deploy/docker-compose.app.yml"

cat >"$MOCK_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

printf '%s\n' "$*" >>"$MOCK_DOCKER_LOG"

case "${1:-}" in
  version)
    echo "Docker 27.0.0"
    ;;
  info)
    ;;
  login)
    cat >/dev/null
    echo "Login Succeeded"
    ;;
  logout)
    ;;
  pull)
    echo "Pulled ${2:-unknown}"
    ;;
  inspect)
    name="${*: -1}"
    if [[ "$*" == *".Config.Image"* ]]; then
      case "$name" in
        aegisops-server) echo "test-user/aegisops:previous-server" ;;
        aegisops-agent) echo "test-user/aegisops:previous-agent" ;;
        aegisops-worker) echo "test-user/aegisops:previous-worker" ;;
        aegisops-runner) echo "test-user/aegisops:previous-runner" ;;
      esac
    elif [[ "$*" == *".State.Health"* ]]; then
      echo "healthy"
    elif [[ "$*" == *".State.Running"* ]]; then
      echo "true"
    fi
    ;;
  compose)
    if [[ "$*" == *" version"* ]]; then
      echo "Docker Compose version v2.30.0"
    fi
    ;;
  image)
    ;;
esac
EOF
chmod +x "$MOCK_BIN/docker"

cat >"$MOCK_BIN/curl" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$MOCK_BIN/curl"

export PATH="$MOCK_BIN:$PATH"
export MOCK_DOCKER_LOG
export HOME="$TMP_ROOT/home"
export TMPDIR="$TMP_ROOT/tmp"
export APP_DIR="$TMP_ROOT/app"
export IMAGE_PREFIX="test-user/aegisops"
export IMAGE_TAG="sha123"
export DOCKERHUB_USERNAME="test-user"
export DOCKERHUB_TOKEN="super-secret-token"

bash "$ROOT_DIR/deploy/scripts/deploy-app.sh"

grep -Fq "login --username test-user --password-stdin" "$MOCK_DOCKER_LOG"
grep -Fq "pull test-user/aegisops:sha123-server" "$MOCK_DOCKER_LOG"
grep -Fq "pull test-user/aegisops:sha123-agent" "$MOCK_DOCKER_LOG"
grep -Fq "pull test-user/aegisops:sha123-worker" "$MOCK_DOCKER_LOG"
grep -Fq "pull test-user/aegisops:sha123-runner" "$MOCK_DOCKER_LOG"
grep -Fq "up -d --remove-orphans --no-build" "$MOCK_DOCKER_LOG"
grep -Fq "logout" "$MOCK_DOCKER_LOG"

if grep -Fq "$DOCKERHUB_TOKEN" "$MOCK_DOCKER_LOG"; then
  echo "Docker Hub token leaked into command logs." >&2
  exit 1
fi

echo "Deployment script simulation passed."

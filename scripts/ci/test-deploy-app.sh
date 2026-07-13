#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEPLOY_SCRIPT="${DEPLOY_SCRIPT:-$ROOT_DIR/deploy/scripts/deploy-app.sh}"
TMP_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

make_mock_env() {
  local case_name=$1
  local mode=$2
  local case_dir="$TMP_ROOT/$case_name"
  local mock_bin="$case_dir/bin"

  mkdir -p "$mock_bin" "$case_dir/app/deploy" "$case_dir/home" "$case_dir/tmp"
  printf 'services: {}\n' >"$case_dir/app/deploy/docker-compose.app.yml"
  : >"$case_dir/docker.log"

  cat >"$mock_bin/docker" <<'MOCK_DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >>"$MOCK_DOCKER_LOG"

last_arg="${*: -1}"
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
    if [[ "$*" == *"{{.Name}}"* ]]; then
      case "$last_arg" in
        stale123) echo "/legacy-aegisops-server" ;;
        foreign123) echo "/foreign-web" ;;
        *) echo "/$last_arg" ;;
      esac
    elif [[ "$*" == *".Config.Image"* ]]; then
      case "$last_arg" in
        aegisops-server) echo "test-user/aegisops:previous-server" ;;
        aegisops-agent) echo "test-user/aegisops:previous-agent" ;;
        aegisops-worker) echo "test-user/aegisops:previous-worker" ;;
        aegisops-runner) echo "test-user/aegisops:previous-runner" ;;
        stale123) echo "test-user/aegisops:legacy-server" ;;
        foreign123) echo "nginx:latest" ;;
      esac
    elif [[ "$*" == *".State.Health"* ]]; then
      echo "healthy"
    elif [[ "$*" == *".State.Running"* ]]; then
      echo "true"
    fi
    ;;
  ps)
    if [[ "$*" == *"publish=8080"* ]]; then
      case "$MOCK_CONFLICT_MODE" in
        stale)
          if [ ! -f "$MOCK_STATE_DIR/stale-removed" ]; then
            if [[ "$*" == *"{{.ID}}"* ]]; then
              echo "stale123"
            else
              echo "container=stale123 name=legacy-aegisops-server image=test-user/aegisops:legacy-server ports=0.0.0.0:8080->8080/tcp"
            fi
          fi
          ;;
        foreign)
          if [[ "$*" == *"{{.ID}}"* ]]; then
            echo "foreign123"
          else
            echo "container=foreign123 name=foreign-web image=nginx:latest ports=0.0.0.0:8080->80/tcp"
          fi
          ;;
      esac
    fi
    ;;
  rm)
    if [ "${2:-}" = "-f" ] && [ "${3:-}" = "stale123" ]; then
      touch "$MOCK_STATE_DIR/stale-removed"
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
MOCK_DOCKER
  chmod +x "$mock_bin/docker"

  cat >"$mock_bin/curl" <<'MOCK_CURL'
#!/usr/bin/env bash
exit 0
MOCK_CURL
  chmod +x "$mock_bin/curl"

  cat >"$mock_bin/ss" <<'MOCK_SS'
#!/usr/bin/env bash
set -Eeuo pipefail
if [ "$MOCK_CONFLICT_MODE" = "host" ]; then
  if [[ "$*" == *"-H"* ]]; then
    echo "LISTEN 0 4096 0.0.0.0:8080 0.0.0.0:*"
  else
    echo "LISTEN 0 4096 0.0.0.0:8080 0.0.0.0:* users:((\"java\",pid=1234,fd=7))"
  fi
  exit 0
fi

if [ "$MOCK_CONFLICT_MODE" = "stale" ] && [ ! -f "$MOCK_STATE_DIR/stale-removed" ]; then
  if [[ "$*" == *"-H"* ]]; then
    echo "LISTEN 0 4096 0.0.0.0:8080 0.0.0.0:*"
  else
    echo "LISTEN 0 4096 0.0.0.0:8080 0.0.0.0:* users:((\"docker-proxy\",pid=2222,fd=7))"
  fi
  exit 0
fi

exit 0
MOCK_SS
  chmod +x "$mock_bin/ss"

  printf '%s\n' "$case_dir" "$mock_bin"
}

run_case() {
  local case_name=$1
  local mode=$2
  local expected=$3
  local values case_dir mock_bin status=0

  values="$(make_mock_env "$case_name" "$mode")"
  case_dir="$(printf '%s\n' "$values" | sed -n '1p')"
  mock_bin="$(printf '%s\n' "$values" | sed -n '2p')"

  set +e
  PATH="$mock_bin:$PATH" \
  MOCK_DOCKER_LOG="$case_dir/docker.log" \
  MOCK_STATE_DIR="$case_dir" \
  MOCK_CONFLICT_MODE="$mode" \
  HOME="$case_dir/home" \
  TMPDIR="$case_dir/tmp" \
  APP_DIR="$case_dir/app" \
  IMAGE_PREFIX="test-user/aegisops" \
  IMAGE_TAG="sha123" \
  DOCKERHUB_USERNAME="test-user" \
  DOCKERHUB_TOKEN="super-secret-token" \
    bash "$DEPLOY_SCRIPT" >"$case_dir/output.log" 2>&1
  status=$?
  set -e

  if [ "$expected" = "success" ] && [ "$status" -ne 0 ]; then
    cat "$case_dir/output.log" >&2
    echo "Case $case_name unexpectedly failed." >&2
    exit 1
  fi
  if [ "$expected" = "failure" ] && [ "$status" -eq 0 ]; then
    cat "$case_dir/output.log" >&2
    echo "Case $case_name unexpectedly succeeded." >&2
    exit 1
  fi

  printf '%s\n' "$case_dir"
}

stale_dir="$(run_case stale-managed stale success)"
grep -Fq "rm -f stale123" "$stale_dir/docker.log"
grep -Fq "up -d --remove-orphans --no-build" "$stale_dir/docker.log"
grep -Fq "logout" "$stale_dir/docker.log"
grep -Fq "Removing stale AegisOps container" "$stale_dir/output.log"

foreign_dir="$(run_case foreign-container foreign failure)"
if grep -Fq "rm -f foreign123" "$foreign_dir/docker.log"; then
  echo "Foreign container was removed unexpectedly." >&2
  exit 1
fi
grep -Fq "refusing to stop it automatically" "$foreign_dir/output.log"

host_dir="$(run_case host-process host failure)"
grep -Fq "occupied by a host process" "$host_dir/output.log"

for dir in "$stale_dir" "$foreign_dir" "$host_dir"; do
  if grep -Fq "super-secret-token" "$dir/docker.log" "$dir/output.log"; then
    echo "Docker Hub token leaked in case $dir." >&2
    exit 1
  fi
done

echo "Deployment port-conflict simulation passed."

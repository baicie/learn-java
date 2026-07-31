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
  printf 'services: {}\n# active descriptor release-a\n' >"$case_dir/app/deploy/docker-compose.app.yml"
  printf 'services: {}\n# candidate descriptor release-b\n' >"$case_dir/app/deploy/docker-compose.app.candidate.yml"
  printf 'services: {}\n# stale previous descriptor\n' >"$case_dir/app/deploy/docker-compose.app.previous.yml"
  case "$mode" in
    first-deploy-*)
      rm -f \
        "$case_dir/app/deploy/docker-compose.app.yml" \
        "$case_dir/app/deploy/docker-compose.app.previous.yml"
      ;;
  esac
  : >"$case_dir/docker.log"
  printf '0\n' >"$case_dir/compose-up-count"

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
    if [ "$MOCK_CONFLICT_MODE" = "image-pull-failure" ]; then
      echo "simulated image pull failure" >&2
      exit 1
    fi
    echo "Pulled ${2:-unknown}"
    ;;
  inspect)
    if [[ "$*" == *"{{.Name}}"* ]]; then
      case "$last_arg" in
        stale123) echo "/legacy-aegisops-server" ;;
        foreign123) echo "/foreign-web" ;;
        *) echo "/$last_arg" ;;
      esac
    elif [[ "$*" == *".Config.Env"* ]]; then
      case "$MOCK_CONFLICT_MODE:$last_arg" in
        rollback:*|rollback-health-failure:*|auth-probe-failure:*|rollback-auth-probe-failure:*)
          echo "AIOPS_SERVICE_AUTH_CONTRACT=oauth2-v1"
          ;;
        mixed-rollback:aegisops-server|mixed-rollback:aegisops-worker)
          echo "AIOPS_SERVICE_AUTH_CONTRACT=oauth2-v1"
          ;;
        mixed-rollback:aegisops-agent)
          echo "AIOPS_SERVICE_AUTH_CONTRACT=static-v1"
          ;;
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
      up_count="$(cat "$MOCK_STATE_DIR/compose-up-count")"
      if [[ "$MOCK_CONFLICT_MODE" == *"rollback"* ]] \
        && [ "$last_arg" = "aegisops-worker" ] \
        && [ "$up_count" = "1" ]; then
        echo "unhealthy"
      elif [ "$MOCK_CONFLICT_MODE" = "first-deploy-health-failure" ] \
        && [ "$last_arg" = "aegisops-worker" ] \
        && [ "$up_count" = "1" ]; then
        echo "unhealthy"
      elif [ "$MOCK_CONFLICT_MODE" = "rollback-health-failure" ] \
        && [ "$last_arg" = "aegisops-agent" ] \
        && [ "$up_count" = "2" ]; then
        echo "unhealthy"
      else
        echo "healthy"
      fi
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
    if [[ "$*" == *" logs "* ]]; then
      printf '%s\n' '{"Authorization":"Bearer deploy-json-oauth-token","X-AegisOps-Diagnosis-Grant":"deploy-json-diagnosis-grant","access_token":"deploy-json-access-token"}'
      printf "%s\n" "{'Authorization': 'Bearer deploy-python-oauth-token', 'X-AegisOps-Diagnosis-Grant': 'deploy-python-diagnosis-grant'}"
      printf '%s\n' 'Authorization: Bearer deploy-plain-oauth-token'
      printf '%s\n' 'X-AegisOps-Diagnosis-Grant=deploy-plain-diagnosis-grant'
    fi
    if [[ "$*" == *"docker-compose.app.candidate.yml config --quiet"* ]] \
      && [ "$MOCK_CONFLICT_MODE" = "compose-validation-failure" ]; then
      echo "simulated candidate validation failure" >&2
      exit 1
    fi
    if [[ "$*" == *" up -d "* ]]; then
      up_count="$(cat "$MOCK_STATE_DIR/compose-up-count")"
      printf '%s\n' "$((up_count + 1))" >"$MOCK_STATE_DIR/compose-up-count"
    fi
    ;;
  image)
    ;;
esac
MOCK_DOCKER
  chmod +x "$mock_bin/docker"

  cat >"$mock_bin/sleep" <<'MOCK_SLEEP'
#!/usr/bin/env bash
exit 0
MOCK_SLEEP
  chmod +x "$mock_bin/sleep"

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

  cat >"$case_dir/service-auth-probe" <<'MOCK_PROBE'
#!/usr/bin/env python3
import os
import sys
from pathlib import Path

up_count = (Path(os.environ["MOCK_STATE_DIR"]) / "compose-up-count").read_text().strip()
if os.environ["MOCK_CONFLICT_MODE"] in {
    "auth-probe-failure",
    "rollback-auth-probe-failure",
}:
    print("simulated service-auth probe failure", file=sys.stderr)
    raise SystemExit(1)
MOCK_PROBE
  chmod +x "$case_dir/service-auth-probe"

  printf '%s\n' "$case_dir" "$mock_bin"
}

execute_case() {
  local case_dir=$1
  local mode=$2
  local expected=$3
  local image_tag=${4:-sha123}
  local mock_bin="$case_dir/bin"
  local status=0

  set +e
  PATH="$mock_bin:$PATH" \
  MOCK_DOCKER_LOG="$case_dir/docker.log" \
  MOCK_STATE_DIR="$case_dir" \
  MOCK_CONFLICT_MODE="$mode" \
  HOME="$case_dir/home" \
  TMPDIR="$case_dir/tmp" \
  APP_DIR="$case_dir/app" \
  IMAGE_PREFIX="test-user/aegisops" \
  IMAGE_TAG="$image_tag" \
  SERVICE_AUTH_PROBE_SCRIPT="$case_dir/service-auth-probe" \
  RUNTIME_REDACTOR_SCRIPT="$ROOT_DIR/deploy/scripts/redact-runtime-output.py" \
  DOCKERHUB_USERNAME="test-user" \
  DOCKERHUB_TOKEN="super-secret-token" \
    bash "$DEPLOY_SCRIPT" >"$case_dir/output.log" 2>&1
  status=$?
  set -e

  if [ "$expected" = "success" ] && [ "$status" -ne 0 ]; then
    cat "$case_dir/output.log" >&2
    echo "Case $case_dir unexpectedly failed." >&2
    exit 1
  fi
  if [ "$expected" = "failure" ] && [ "$status" -eq 0 ]; then
    cat "$case_dir/output.log" >&2
    echo "Case $case_dir unexpectedly succeeded." >&2
    exit 1
  fi
}

run_case() {
  local case_name=$1
  local mode=$2
  local expected=$3
  local values case_dir

  values="$(make_mock_env "$case_name" "$mode")"
  case_dir="$(printf '%s\n' "$values" | sed -n '1p')"

  execute_case "$case_dir" "$mode" "$expected"

  printf '%s\n' "$case_dir"
}

stale_dir="$(run_case stale-managed stale success)"
grep -Fq "rm -f stale123" "$stale_dir/docker.log"
grep -Fq "rm -f aegisops-server" "$stale_dir/docker.log"
grep -Fq "rm -f aegisops-agent" "$stale_dir/docker.log"
grep -Fq "rm -f aegisops-worker" "$stale_dir/docker.log"
grep -Fq "rm -f aegisops-runner" "$stale_dir/docker.log"
grep -Fq "up -d --remove-orphans --no-build" "$stale_dir/docker.log"
grep -Fq "logout" "$stale_dir/docker.log"
grep -Fq "Removing stale AegisOps container" "$stale_dir/output.log"
grep -Fq "Recreating stateless application containers" "$stale_dir/output.log"
grep -Fq "candidate descriptor release-b" "$stale_dir/app/deploy/docker-compose.app.yml"
grep -Fq "active descriptor release-a" "$stale_dir/app/deploy/docker-compose.app.previous.yml"
[ ! -e "$stale_dir/app/deploy/docker-compose.app.candidate.yml" ]

validation_dir="$(run_case invalid-candidate compose-validation-failure failure)"
grep -Fq "simulated candidate validation failure" "$validation_dir/output.log"
grep -Fq "active descriptor release-a" "$validation_dir/app/deploy/docker-compose.app.yml"
grep -Fq "stale previous descriptor" "$validation_dir/app/deploy/docker-compose.app.previous.yml"
grep -Fq "candidate descriptor release-b" "$validation_dir/app/deploy/docker-compose.app.candidate.yml"

pull_failure_dir="$(run_case failed-image-pull image-pull-failure failure)"
grep -Fq "simulated image pull failure" "$pull_failure_dir/output.log"
grep -Fq "active descriptor release-a" "$pull_failure_dir/app/deploy/docker-compose.app.yml"
grep -Fq "active descriptor release-a" "$pull_failure_dir/app/deploy/docker-compose.app.previous.yml"
grep -Fq "candidate descriptor release-b" "$pull_failure_dir/app/deploy/docker-compose.app.candidate.yml"

foreign_dir="$(run_case foreign-container foreign failure)"
if grep -Fq "rm -f foreign123" "$foreign_dir/docker.log"; then
  echo "Foreign container was removed unexpectedly." >&2
  exit 1
fi
grep -Fq "refusing to stop it automatically" "$foreign_dir/output.log"
grep -Fq "active descriptor release-a" "$foreign_dir/app/deploy/docker-compose.app.yml"

host_dir="$(run_case host-process host failure)"
grep -Fq "occupied by a host process" "$host_dir/output.log"
grep -Fq "active descriptor release-a" "$host_dir/app/deploy/docker-compose.app.yml"

rollback_dir="$(run_case unhealthy-worker rollback failure)"
grep -Fq "aegisops-worker status=unhealthy" "$rollback_dir/output.log"
grep -Fq "Rolling back the previous OAuth2-compatible release" "$rollback_dir/output.log"
grep -Fq "docker-compose.app.previous.yml up -d --remove-orphans --no-build" "$rollback_dir/docker.log"
[ "$(cat "$rollback_dir/compose-up-count")" = "2" ]
[ "$(grep -Fc "rm -f aegisops-server" "$rollback_dir/docker.log")" -eq 2 ]
[ "$(grep -Fc "rm -f aegisops-worker" "$rollback_dir/docker.log")" -eq 2 ]
grep -Fq "active descriptor release-a" "$rollback_dir/app/deploy/docker-compose.app.yml"
grep -Fq "active descriptor release-a" "$rollback_dir/app/deploy/docker-compose.app.previous.yml"
grep -Fq "candidate descriptor release-b" "$rollback_dir/app/deploy/docker-compose.app.candidate.yml"

rollback_health_failure_dir="$(run_case unhealthy-previous-release rollback-health-failure failure)"
grep -Fq "Rolling back the previous OAuth2-compatible release" "$rollback_health_failure_dir/output.log"
grep -Fq "Automatic rollback failed health checks" "$rollback_health_failure_dir/output.log"
grep -Fq "active descriptor release-a" "$rollback_health_failure_dir/app/deploy/docker-compose.app.yml"
grep -Fq "active descriptor release-a" "$rollback_health_failure_dir/app/deploy/docker-compose.app.previous.yml"

legacy_rollback_dir="$(run_case legacy-auth-release legacy-rollback failure)"
grep -Fq "incompatible service-auth contract" "$legacy_rollback_dir/output.log"
[ "$(cat "$legacy_rollback_dir/compose-up-count")" = "1" ]
if grep -Fq "docker-compose.app.previous.yml up -d" "$legacy_rollback_dir/docker.log"; then
  echo "Legacy authentication release was rolled back automatically." >&2
  exit 1
fi
grep -Fq "active descriptor release-a" "$legacy_rollback_dir/app/deploy/docker-compose.app.yml"

mixed_rollback_dir="$(run_case mixed-auth-release mixed-rollback failure)"
grep -Fq "aegisops-agent has incompatible service-auth contract" "$mixed_rollback_dir/output.log"
[ "$(cat "$mixed_rollback_dir/compose-up-count")" = "1" ]
if grep -Fq "docker-compose.app.previous.yml up -d" "$mixed_rollback_dir/docker.log"; then
  echo "Mixed authentication release was rolled back automatically." >&2
  exit 1
fi
grep -Fq "active descriptor release-a" "$mixed_rollback_dir/app/deploy/docker-compose.app.yml"

auth_probe_failure_dir="$(run_case failed-auth-probe auth-probe-failure failure)"
grep -Fq "simulated service-auth probe failure" "$auth_probe_failure_dir/output.log"
grep -Fq "Rolling back the previous OAuth2-compatible release" "$auth_probe_failure_dir/output.log"
grep -Fq "[REDACTED]" "$auth_probe_failure_dir/output.log"
[ "$(cat "$auth_probe_failure_dir/compose-up-count")" = "2" ]
grep -Fq "active descriptor release-a" "$auth_probe_failure_dir/app/deploy/docker-compose.app.yml"
grep -Fq "active descriptor release-a" "$auth_probe_failure_dir/app/deploy/docker-compose.app.previous.yml"

rollback_auth_failure_dir="$(run_case failed-rollback-auth-probe rollback-auth-probe-failure failure)"
grep -Fq "Rolling back the previous OAuth2-compatible release" "$rollback_auth_failure_dir/output.log"
grep -Fq "Automatic rollback failed the service-auth probe" "$rollback_auth_failure_dir/output.log"
grep -Fq "active descriptor release-a" "$rollback_auth_failure_dir/app/deploy/docker-compose.app.yml"
grep -Fq "active descriptor release-a" "$rollback_auth_failure_dir/app/deploy/docker-compose.app.previous.yml"

first_success_dir="$(run_case first-release first-deploy-success success)"
grep -Fq "candidate descriptor release-b" "$first_success_dir/app/deploy/docker-compose.app.yml"
[ ! -e "$first_success_dir/app/deploy/docker-compose.app.candidate.yml" ]
[ ! -e "$first_success_dir/app/deploy/docker-compose.app.previous.yml" ]

first_failure_dir="$(run_case failed-first-release first-deploy-health-failure failure)"
[ ! -e "$first_failure_dir/app/deploy/docker-compose.app.yml" ]
[ ! -e "$first_failure_dir/app/deploy/docker-compose.app.previous.yml" ]
grep -Fq "candidate descriptor release-b" "$first_failure_dir/app/deploy/docker-compose.app.candidate.yml"

consecutive_dir="$(run_case failed-release-b image-pull-failure failure)"
printf 'services: {}\n# candidate descriptor release-c\n' \
  >"$consecutive_dir/app/deploy/docker-compose.app.candidate.yml"
printf '0\n' >"$consecutive_dir/compose-up-count"
: >"$consecutive_dir/docker.log"
execute_case "$consecutive_dir" success success sha-c
grep -Fq "candidate descriptor release-c" "$consecutive_dir/app/deploy/docker-compose.app.yml"
grep -Fq "active descriptor release-a" "$consecutive_dir/app/deploy/docker-compose.app.previous.yml"
[ ! -e "$consecutive_dir/app/deploy/docker-compose.app.candidate.yml" ]

for dir in \
  "$stale_dir" \
  "$validation_dir" \
  "$pull_failure_dir" \
  "$foreign_dir" \
  "$host_dir" \
  "$rollback_dir" \
  "$rollback_health_failure_dir" \
  "$legacy_rollback_dir" \
  "$mixed_rollback_dir" \
  "$auth_probe_failure_dir" \
  "$rollback_auth_failure_dir" \
  "$first_success_dir" \
  "$first_failure_dir" \
  "$consecutive_dir"; do
  if grep -Fq "super-secret-token" "$dir/docker.log" "$dir/output.log"; then
    echo "Docker Hub token leaked in case $dir." >&2
    exit 1
  fi
  for credential in \
    deploy-json-oauth-token \
    deploy-json-diagnosis-grant \
    deploy-json-access-token \
    deploy-python-oauth-token \
    deploy-python-diagnosis-grant \
    deploy-plain-oauth-token \
    deploy-plain-diagnosis-grant; do
    if grep -Fq "$credential" "$dir/output.log"; then
      echo "Runtime credential leaked in case $dir." >&2
      exit 1
    fi
  done
done

echo "Deployment runtime and port-conflict simulation passed."

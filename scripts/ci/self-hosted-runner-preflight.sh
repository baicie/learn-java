#!/usr/bin/env bash
set -euo pipefail

CACHE_ROOT="${AIOPS_CI_CACHE_ROOT:-$HOME/.cache/aegisops-ci}"
GITHUB_ENV_FILE="${GITHUB_ENV:-}"

require_command() {
  local name=$1
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Required command is not available on the self-hosted runner: $name" >&2
    exit 1
  fi
}

export_for_job() {
  local name=$1
  local value=$2
  if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
    echo "Refusing multiline GitHub Actions environment value: $name" >&2
    exit 1
  fi
  export "$name=$value"
  if [ -n "$GITHUB_ENV_FILE" ]; then
    printf '%s=%s\n' "$name" "$value" >> "$GITHUB_ENV_FILE"
  fi
}

require_major() {
  local name=$1
  local actual=$2
  local expected=$3
  if [ "$actual" != "$expected" ]; then
    echo "$name major version must be $expected, got $actual" >&2
    exit 1
  fi
}

require_command bash
require_command git
require_command curl

case "$(uname -m)" in
  x86_64 | amd64) ;;
  *)
    echo "Runner architecture must be x64, got $(uname -m)" >&2
    exit 1
    ;;
esac

mkdir -p \
  "$CACHE_ROOT/maven" \
  "$CACHE_ROOT/pnpm" \
  "$CACHE_ROOT/pip" \
  "$CACHE_ROOT/playwright"

export_for_job MAVEN_OPTS "-Dmaven.repo.local=$CACHE_ROOT/maven"
export_for_job PNPM_STORE_DIR "$CACHE_ROOT/pnpm"
export_for_job PIP_CACHE_DIR "$CACHE_ROOT/pip"
export_for_job PLAYWRIGHT_BROWSERS_PATH "$CACHE_ROOT/playwright"

if [ -n "${AIOPS_NPM_REGISTRY:-}" ]; then
  export_for_job NPM_CONFIG_REGISTRY "$AIOPS_NPM_REGISTRY"
fi
if [ -n "${AIOPS_PIP_INDEX_URL:-}" ]; then
  export_for_job PIP_INDEX_URL "$AIOPS_PIP_INDEX_URL"
fi

for capability in "$@"; do
  case "$capability" in
    java)
      require_command java
      require_command mvn
      java_major="$(java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
      require_major Java "$java_major" 21
      ;;
    node)
      require_command node
      require_command pnpm
      node_major="$(node --version | sed -E 's/^v([0-9]+).*/\1/')"
      require_major Node.js "$node_major" 22
      if [ "$(pnpm --version)" != "10.33.4" ]; then
        echo "pnpm version must be 10.33.4, got $(pnpm --version)" >&2
        exit 1
      fi
      ;;
    python)
      require_command python3
      python_minor="$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')"
      if [ "$python_minor" != "3.12" ]; then
        echo "Python version must be 3.12.x, got $python_minor" >&2
        exit 1
      fi
      ;;
    docker)
      require_command docker
      docker info >/dev/null
      docker buildx version >/dev/null
      ;;
    postgres)
      require_command psql
      ;;
    shellcheck)
      require_command shellcheck
      ;;
    *)
      echo "Unknown runner capability: $capability" >&2
      exit 1
      ;;
  esac
done

echo "Self-hosted runner preflight passed: capabilities=${*:-base} cache=$CACHE_ROOT"

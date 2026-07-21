#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CI_WORKFLOW="$ROOT_DIR/.github/workflows/ci.yml"
OPS_WORKFLOW="$ROOT_DIR/.github/workflows/ops-scripts.yml"
RELEASE_WORKFLOW="$ROOT_DIR/.github/workflows/release-verify.yml"
DEPLOY_WORKFLOW="$ROOT_DIR/.github/workflows/deploy.yml"
E2E_WORKFLOW="$ROOT_DIR/.github/workflows/work-record-e2e.yml"
RELEASE_PREFLIGHT="$ROOT_DIR/scripts/ci/release-preflight.sh"
AGENT_DOCKERFILE="$ROOT_DIR/apps/aiops-agent/Dockerfile"

require_text() {
  local file=$1
  local text=$2
  grep -Fq -- "$text" "$file" || {
    echo "Missing workflow policy in $file: $text" >&2
    exit 1
  }
}

reject_text() {
  local file=$1
  local text=$2
  if grep -Fq -- "$text" "$file"; then
    echo "Forbidden workflow policy in $file: $text" >&2
    exit 1
  fi
}

require_text "$CI_WORKFLOW" "needs: docs"
require_text "$CI_WORKFLOW" "needs: agent"
require_text "$CI_WORKFLOW" "needs: frontend"
require_text "$CI_WORKFLOW" 'if: ${{ !cancelled() }}'
reject_text "$CI_WORKFLOW" "if: always()"
require_text "$OPS_WORKFLOW" "needs: shellcheck"
require_text "$RELEASE_WORKFLOW" "- CI"
require_text "$RELEASE_WORKFLOW" "needs.preflight.outputs.release_required == 'true'"
reject_text "$RELEASE_WORKFLOW" "docker/build-push-action"
reject_text "$RELEASE_WORKFLOW" "cache-to: type=gha"
reject_text "$DEPLOY_WORKFLOW" "cache-to: type=gha"
reject_text "$CI_WORKFLOW" "cache: maven"
reject_text "$CI_WORKFLOW" "cache: pip"
reject_text "$RELEASE_WORKFLOW" "cache: maven"
reject_text "$DEPLOY_WORKFLOW" "cache: maven"
reject_text "$E2E_WORKFLOW" "cache: maven"
require_text "$DEPLOY_WORKFLOW" "Reusing \$image from Release Verify"
require_text "$RELEASE_PREFLIGHT" "AIOPS_AGENT_INTERNAL_TOKEN=preflight-only"
require_text "$RELEASE_WORKFLOW" "AIOPS_AGENT_INTERNAL_TOKEN: runtime-smoke-only"
require_text "$AGENT_DOCKERFILE" "pip install --timeout 300 --retries 10 --no-cache-dir ."

release_build_count="$(grep -Ec '^[[:space:]]+docker build \\' "$RELEASE_WORKFLOW")"
if [ "$release_build_count" -ne 4 ]; then
  echo "Release Verify must build exactly four images once; found $release_build_count." >&2
  exit 1
fi

echo "Workflow resource policy passed."

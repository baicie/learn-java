#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CI_WORKFLOW="$ROOT_DIR/.github/workflows/ci.yml"
OPS_WORKFLOW="$ROOT_DIR/.github/workflows/ops-scripts.yml"
RELEASE_WORKFLOW="$ROOT_DIR/.github/workflows/release-verify.yml"
MANUAL_DOCKER_WORKFLOW="$ROOT_DIR/.github/workflows/manual-docker-build.yml"
E2E_WORKFLOW="$ROOT_DIR/.github/workflows/work-record-e2e.yml"
RELEASE_PREFLIGHT="$ROOT_DIR/scripts/ci/release-preflight.sh"
BACKEND_SCRIPT="$ROOT_DIR/scripts/ci/backend.sh"
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

reject_text "$CI_WORKFLOW" "needs: docs"
reject_text "$CI_WORKFLOW" "needs: agent"
reject_text "$CI_WORKFLOW" "needs: frontend"
require_text "$CI_WORKFLOW" "TZ: Asia/Shanghai"
require_text "$CI_WORKFLOW" 'if: ${{ !cancelled() }}'
reject_text "$CI_WORKFLOW" "if: always()"
require_text "$OPS_WORKFLOW" "needs: shellcheck"
require_text "$RELEASE_WORKFLOW" "- CI"
require_text "$RELEASE_WORKFLOW" "needs.preflight.outputs.release_required == 'true'"
reject_text "$RELEASE_WORKFLOW" "docker/build-push-action"
reject_text "$RELEASE_WORKFLOW" "cache-to: type=gha"
reject_text "$CI_WORKFLOW" "cache: maven"
reject_text "$CI_WORKFLOW" "cache: pip"
reject_text "$RELEASE_WORKFLOW" "cache: maven"
reject_text "$E2E_WORKFLOW" "cache: maven"
for workflow in "$ROOT_DIR"/.github/workflows/*.yml; do
  reject_text "$workflow" "self-hosted"
  reject_text "$workflow" "bash scripts/ci/prepare-docker.sh"
done
require_text "$RELEASE_WORKFLOW" "for attempt in 1 2 3; do"
require_text "$RELEASE_WORKFLOW" 'push_with_digest "$remote_image"'
require_text "$RELEASE_WORKFLOW" 'docker buildx imagetools inspect "$exact_image"'
require_text "$RELEASE_WORKFLOW" "uses: docker/login-action@v4"
require_text "$RELEASE_WORKFLOW" "run: bash scripts/deploy/build-images.sh"
require_text "$RELEASE_WORKFLOW" "deploy/docker-compose.core.yml"
require_text "$RELEASE_WORKFLOW" "deploy/install.sh"
require_text "$RELEASE_WORKFLOW" "verify-internal-mtls.py"
require_text "$RELEASE_WORKFLOW" "--profile automation"
require_text "$MANUAL_DOCKER_WORKFLOW" "workflow_dispatch:"
require_text "$MANUAL_DOCKER_WORKFLOW" 'ref: ${{ inputs.branch }}'
require_text "$MANUAL_DOCKER_WORKFLOW" "run: bash scripts/deploy/build-images.sh"
require_text "$MANUAL_DOCKER_WORKFLOW" 'remote_image="${IMAGE_PREFIX}:${IMAGE_TAG}-${component}"'
require_text "$RELEASE_PREFLIGHT" "deploy/install.sh"
require_text "$RELEASE_PREFLIGHT" "docker-compose.core.yml"
require_text "$RELEASE_WORKFLOW" 'AIOPS_AGENT_OPENAI_API_KEY: ${{ secrets.AIOPS_AGENT_OPENAI_API_KEY }}'
require_text "$RELEASE_WORKFLOW" "envs: STAGING_ID,AIOPS_APP_IMAGE,AIOPS_AGENT_IMAGE,AIOPS_RUNNER_IMAGE,AIOPS_DEPLOY_MODE,AIOPS_AGENT_OPENAI_API_KEY"
require_text "$RELEASE_WORKFLOW" "needs: runtime-smoke"
require_text "$AGENT_DOCKERFILE" "pip install --timeout 300 --retries 10 --no-cache-dir ."
require_text "$BACKEND_SCRIPT" "modules/aiops-worker-runtime"

for forbidden in aiops-worker docker-compose.app.yml docker-compose.idp.yml oauth jwks keycloak; do
  if grep -Fiq -- "$forbidden" "$RELEASE_WORKFLOW"; then
    echo "Forbidden legacy release topology in $RELEASE_WORKFLOW: $forbidden" >&2
    exit 1
  fi
done

if [ -e "$ROOT_DIR/.github/workflows/deploy.yml" ]; then
  echo "Deploy must stay in release-verify.yml to avoid duplicate preflight and smoke jobs." >&2
  exit 1
fi

release_build_count="$(grep -Fc 'run: bash scripts/deploy/build-images.sh' "$RELEASE_WORKFLOW")"
if [ "$release_build_count" -ne 1 ]; then
  echo "Release Verify must invoke the three-image build exactly once; found $release_build_count." >&2
  exit 1
fi

echo "Workflow resource policy passed."

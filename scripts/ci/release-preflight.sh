#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$ROOT_DIR"

echo "==> Release preflight"

MAVEN="mvn"
if [ -x "./mvnw" ]; then
  MAVEN="./mvnw"
elif ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is not found in PATH." >&2
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js is not found in PATH." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not found in PATH." >&2
  exit 1
fi

echo "==> Parse standalone portal POM"
"$MAVEN" -B -ntp -f web/portal/pom.xml validate

echo "==> Validate deployment shell"
bash -n deploy/scripts/deploy-app.sh
bash -n scripts/ci/test-deploy-app.sh
bash scripts/ci/test-deploy-app.sh
grep -Fq 'DEPLOY_STAGE="port-preflight"' deploy/scripts/deploy-app.sh
grep -Fq 'refusing to stop it automatically' scripts/ci/test-deploy-app.sh

echo "==> Validate remote deployment contract"
grep -Fq "bash -lc '" .github/workflows/deploy.yml
grep -Fq "envs: IMAGE_PREFIX,IMAGE_TAG,DOCKERHUB_USERNAME,DOCKERHUB_TOKEN" \
  .github/workflows/deploy.yml
grep -Fq "needs: runtime-smoke" .github/workflows/deploy.yml

echo "==> Validate jOOQ DDL preparation"
TMP_SCHEMA="$(mktemp)"
trap 'rm -f "$TMP_SCHEMA"' EXIT
node scripts/prepare-jooq-ddl.mjs \
  apps/aiops-server/src/main/resources/db/migration \
  "$TMP_SCHEMA"
test -s "$TMP_SCHEMA"

echo "==> Validate Docker Compose interpolation and structure"
AIOPS_SERVER_IMAGE=example.invalid/aegisops:test \
AIOPS_AGENT_IMAGE=example.invalid/aegisops/aiops-agent:test \
AIOPS_WORKER_IMAGE=example.invalid/aegisops/aiops-worker:test \
AIOPS_RUNNER_IMAGE=example.invalid/aegisops/aiops-runner:test \
  docker compose -f deploy/docker-compose.app.yml config --quiet

if grep -R -nE 'DOCKERHUB_USERNAME_REPLACE_ME|SHA_REPLACE_ME' deploy; then
  echo "Deployment files still contain unresolved image placeholders." >&2
  exit 1
fi

echo "Release preflight passed."

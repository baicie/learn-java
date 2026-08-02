#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT_DIR"

echo "==> Release preflight"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is not available: $1" >&2
    exit 1
  }
}

MAVEN="mvn"
if [ -x "./mvnw" ]; then
  MAVEN="./mvnw"
else
  require_command mvn
fi
for command_name in node docker openssl python3; do
  require_command "$command_name"
done

echo "==> Parse release POMs"
"$MAVEN" -B -ntp -f web/portal/pom.xml validate
"$MAVEN" -B -ntp -f apps/aiops-server/pom.xml validate
"$MAVEN" -B -ntp -f apps/aiops-runner/pom.xml validate

echo "==> Validate process and module boundaries"
if grep -Fq '<artifactId>aiops-security</artifactId>' modules/aiops-observability/pom.xml; then
  echo "aiops-observability must not pull the complete security runtime into Runner." >&2
  exit 1
fi
grep -Fq '<artifactId>aiops-worker-runtime</artifactId>' apps/aiops-server/pom.xml
"$MAVEN" -B -ntp -pl modules/aiops-observability -am test

echo "==> Validate executable image entrypoints"
grep -Fq '<goal>repackage</goal>' apps/aiops-server/pom.xml
grep -Fq '<goal>repackage</goal>' apps/aiops-runner/pom.xml
grep -Fq 'apps/aiops-server' apps/aiops-server/Dockerfile
grep -Fq 'apps/aiops-runner' apps/aiops-runner/Dockerfile
grep -Fq 'aiops_agent.serve' apps/aiops-agent/Dockerfile

echo "==> Validate deployment shell"
for script in \
  deploy/install.sh \
  deploy/scripts/backup-core.sh \
  deploy/scripts/backup-zabbix.sh \
  deploy/scripts/deploy-app.sh \
  deploy/scripts/deploy-zabbix.sh \
  deploy/scripts/ensure-zabbix-api-network.sh \
  deploy/scripts/configure-docker-mirror.sh \
  deploy/scripts/promote-deployment-candidate.sh \
  deploy/scripts/restore-core.sh \
  deploy/scripts/restore-zabbix.sh \
  scripts/deploy/build-images.sh \
  scripts/deploy/deploy-app.sh \
  scripts/ci/test-deploy-zabbix.sh \
  scripts/ci/test-configure-docker-mirror.sh \
  scripts/ci/test-workflow-resource-policy.sh; do
  bash -n "$script"
done
bash scripts/ci/test-deploy-zabbix.sh
bash scripts/ci/test-configure-docker-mirror.sh
bash scripts/ci/test-workflow-resource-policy.sh

echo "==> Validate jOOQ migration input"
TMP_SCHEMA="$(mktemp)"
TMP_RUNTIME_ROOT="$(mktemp -d)"
cleanup() {
  rm -f "$TMP_SCHEMA"
  rm -rf "$TMP_RUNTIME_ROOT"
}
trap cleanup EXIT
node scripts/prepare-jooq-ddl.mjs \
  apps/aiops-server/src/main/resources/db/migration \
  "$TMP_SCHEMA"
test -s "$TMP_SCHEMA"

echo "==> Generate and validate the canonical Compose deployment"
bash deploy/install.sh \
  --mode automation \
  --runtime-dir "$TMP_RUNTIME_ROOT/runtime" \
  --no-start
AIOPS_APP_IMAGE=example.invalid/aegisops:test-app \
AIOPS_AGENT_IMAGE=example.invalid/aegisops:test-agent \
AIOPS_RUNNER_IMAGE=example.invalid/aegisops:test-runner \
  docker compose \
    --env-file "$TMP_RUNTIME_ROOT/runtime/.env" \
    -f deploy/docker-compose.core.yml \
    --profile ai \
    --profile automation \
    config --quiet

ZABBIX_DB_PASSWORD=preflight-only \
  docker compose -f deploy/docker-compose.zabbix.yml config --quiet

if grep -R -nE 'DOCKERHUB_USERNAME_REPLACE_ME|SHA_REPLACE_ME' deploy; then
  echo "Deployment files still contain unresolved image placeholders." >&2
  exit 1
fi

echo "Release preflight passed."

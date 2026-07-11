#!/usr/bin/env bash
set -euo pipefail

echo "==> Backend CI"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$ROOT_DIR"

if [ ! -f "pom.xml" ]; then
  echo "Skip backend: pom.xml not found."
  exit 0
fi

MAVEN="mvn"
if [ -x "./mvnw" ]; then
  MAVEN="./mvnw"
elif ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is not found in PATH." >&2
  exit 1
fi

# Phase 17：单元测试先走一遍（兼容 Phase 1 baseline 检查）。
"${MAVEN}" -B -ntp -pl modules/aiops-platform -am test
"${MAVEN}" -B -ntp -pl modules/aiops-work-record -am test
"${MAVEN}" -B -ntp -pl apps/aiops-server -am test

# Phase 17：完整验证（包含 Failsafe IT 与 JaCoCo 覆盖率门禁）。
echo "==> Verify backend test system"

"${MAVEN}" \
  -B \
  -ntp \
  -pl modules/aiops-platform,modules/aiops-work-record,apps/aiops-server \
  -am \
  verify
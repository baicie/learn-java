#!/usr/bin/env bash
set -euo pipefail

echo "==> Backend CI"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$ROOT_DIR"

if [ ! -f "pom.xml" ]; then
  echo "pom.xml not found." >&2
  exit 1
fi

MAVEN="mvn"

if [ -x "./mvnw" ]; then
  MAVEN="./mvnw"
elif ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is not found in PATH." >&2
  exit 1
fi

echo "==> Verify platform, work-record and server"

"${MAVEN}" \
  -B \
  -ntp \
  -pl modules/aiops-platform,modules/aiops-work-record,apps/aiops-server \
  -am \
  verify

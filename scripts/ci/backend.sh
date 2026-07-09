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

echo "==> Test aiops-platform"
"${MAVEN}" -B -ntp -pl modules/aiops-platform -am test

echo "==> Test aiops-work-record"
"${MAVEN}" -B -ntp -pl modules/aiops-work-record -am test

echo "==> Test aiops-server"
"${MAVEN}" -B -ntp -pl apps/aiops-server -am test

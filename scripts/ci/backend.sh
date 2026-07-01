#!/usr/bin/env bash
set -euo pipefail

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

"${MAVEN}" -B -ntp -DskipITs=true -DskipE2E=true verify

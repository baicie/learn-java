#!/usr/bin/env bash
set -euo pipefail

echo "==> Backend CI"

if [ ! -f "pom.xml" ]; then
  echo "Skip backend: pom.xml not found."
  exit 0
fi

if [ -x "./mvnw" ]; then
  MVN="./mvnw"
else
  MVN="mvn"
fi

echo "Using Maven: ${MVN}"

${MVN} -B -ntp \
  -DskipITs=true \
  -DskipE2E=true \
  -Dspotless.check.skip=true \
  verify

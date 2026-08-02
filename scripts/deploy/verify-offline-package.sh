#!/usr/bin/env bash
set -euo pipefail

PACKAGE_DIR="${1:-.}"

required_files=(
  "$PACKAGE_DIR/values/values-offline.yaml"
  "$PACKAGE_DIR/init/001-create-roles.sql"
  "$PACKAGE_DIR/init/002-grant-runner.sql"
  "$PACKAGE_DIR/scripts/load-offline-images.sh"
  "$PACKAGE_DIR/scripts/generate-secrets.sh"
  "$PACKAGE_DIR/scripts/render-helm.sh"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing required file: $file" >&2
    exit 1
  fi
done

if [[ ! -f "$PACKAGE_DIR/images/aegisops-images.tar" ]]; then
  if [[ ! -f "$PACKAGE_DIR/images/manifest.txt" ]] \
    || ! compgen -G "$PACKAGE_DIR/images/aegisops-images.tar.vol*" >/dev/null; then
    echo "Missing image archive or split image volumes in $PACKAGE_DIR/images" >&2
    exit 1
  fi
fi

chart_count="$(find "$PACKAGE_DIR/chart" -name 'aegisops-*.tgz' | wc -l | tr -d ' ')"
if [[ "$chart_count" == "0" ]]; then
  echo "Missing Helm chart package in $PACKAGE_DIR/chart" >&2
  exit 1
fi

echo "Offline package verification passed: $PACKAGE_DIR"

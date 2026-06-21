#!/usr/bin/env bash
set -euo pipefail

PACKAGE_DIR="${1:-.}"

required_files=(
  "$PACKAGE_DIR/images/aegisops-images.tar"
  "$PACKAGE_DIR/values/values-offline.yaml"
  "$PACKAGE_DIR/scripts/load-offline-images.sh"
  "$PACKAGE_DIR/scripts/render-helm.sh"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing required file: $file" >&2
    exit 1
  fi
done

chart_count="$(find "$PACKAGE_DIR/chart" -name 'aegisops-*.tgz' | wc -l | tr -d ' ')"
if [[ "$chart_count" == "0" ]]; then
  echo "Missing Helm chart package in $PACKAGE_DIR/chart" >&2
  exit 1
fi

echo "Offline package verification passed: $PACKAGE_DIR"

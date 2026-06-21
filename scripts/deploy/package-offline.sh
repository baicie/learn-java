#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:-0.1.0}"
REGISTRY="${REGISTRY:-aegisops}"
OUT_DIR="${OUT_DIR:-dist/offline/aegisops-${VERSION}}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/images" "$OUT_DIR/chart" "$OUT_DIR/values" "$OUT_DIR/scripts"

IMAGE_LIST=(
  "${REGISTRY}/aiops-server:${VERSION}"
  "${REGISTRY}/aiops-worker:${VERSION}"
  "${REGISTRY}/aiops-runner:${VERSION}"
  "${REGISTRY}/aiops-agent:${VERSION}"
)

docker save "${IMAGE_LIST[@]}" -o "$OUT_DIR/images/aegisops-images.tar"

helm package deploy/helm/aegisops --destination "$OUT_DIR/chart"

cp deploy/helm/aegisops/values-offline.yaml "$OUT_DIR/values/values-offline.yaml"
cp deploy/offline/README.md "$OUT_DIR/README.md"
cp scripts/deploy/load-offline-images.sh "$OUT_DIR/scripts/load-offline-images.sh"
cp scripts/deploy/render-helm.sh "$OUT_DIR/scripts/render-helm.sh"
cp scripts/deploy/verify-offline-package.sh "$OUT_DIR/scripts/verify-offline-package.sh"

chmod +x "$OUT_DIR/scripts/"*.sh

tar -czf "dist/offline/aegisops-${VERSION}.tar.gz" -C "dist/offline" "aegisops-${VERSION}"

echo "Offline package created: dist/offline/aegisops-${VERSION}.tar.gz"

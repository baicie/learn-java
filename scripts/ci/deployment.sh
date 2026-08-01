#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CHART_DIR="$ROOT_DIR/deploy/helm/aegisops"
RELEASE="ci"

cd "$ROOT_DIR"

if ! command -v helm >/dev/null 2>&1; then
  echo "Helm is not found in PATH." >&2
  exit 1
fi

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aegisops-deployment-ci.XXXXXX")"
cleanup() {
  rm -rf -- "$TEMP_DIR"
}
trap cleanup EXIT HUP INT TERM

GENERATED_VALUES="$TEMP_DIR/generated-secrets.values.yaml"
RENDERED="$TEMP_DIR/rendered.yaml"
bash scripts/deploy/generate-secrets.sh "$GENERATED_VALUES"

HELM_ARGS=(
  --values "$GENERATED_VALUES"
  --set external.postgres.host=postgres.example.internal
  --set apps.runner.enabled=true
  --set networkPolicy.enabled=true
  --set-string 'networkPolicy.egress.allowedCidrs[0]=10.0.0.0/8'
  --set serviceMesh.istio.enabled=true
)

helm lint "$CHART_DIR" "${HELM_ARGS[@]}"
helm template "$RELEASE" "$CHART_DIR" "${HELM_ARGS[@]}" > "$RENDERED"

for component in app agent runner; do
  grep -Fq "app.kubernetes.io/component: $component" "$RENDERED" || {
    echo "Rendered chart is missing component: $component" >&2
    exit 1
  }
done

for forbidden in aiops-worker keycloak jwks oauth2; do
  if grep -Fiq -- "$forbidden" "$RENDERED"; then
    echo "Rendered chart contains legacy internal authentication: $forbidden" >&2
    exit 1
  fi
done

python3 -m pytest deploy/helm/aegisops/tests deploy/tests -q

echo "Deployment security checks passed."

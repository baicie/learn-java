#!/usr/bin/env bash
set -euo pipefail

RELEASE="${RELEASE:-aegisops}"
NAMESPACE="${NAMESPACE:-aegisops}"
VALUES="${1:-deploy/helm/aegisops/values.yaml}"

helm template "$RELEASE" deploy/helm/aegisops \
  --namespace "$NAMESPACE" \
  -f "$VALUES"

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CHART_DIR="$ROOT_DIR/deploy/helm/aegisops"
RELEASE="ci"

if ! command -v helm >/dev/null 2>&1; then
  echo "Helm is not found in PATH." >&2
  exit 1
fi

RENDERED="$(mktemp)"
trap 'rm -f "$RENDERED"' EXIT

HELM_VALUES=(
  --set security.jwtSecret=ci-jwt-secret-change-me
  --set security.zabbixWebhookSigningSecret=ci-webhook-secret
  --set security.diagnosisGrantSecret=ci-diagnosis-grant-secret-change-me
  --set security.serviceAuth.mode=oauth2
  --set security.serviceAuth.issuerUri=https://idp.example.com/realms/aegisops
  --set security.serviceAuth.jwkSetUri=https://idp.example.com/realms/aegisops/certs
  --set security.serviceAuth.tokenUri=https://idp.example.com/realms/aegisops/token
  --set security.serviceAuth.clients.server.clientId=aiops-server
  --set security.serviceAuth.clients.server.clientSecret=server-secret
  --set security.serviceAuth.clients.worker.clientId=aiops-worker
  --set security.serviceAuth.clients.worker.clientSecret=worker-secret
  --set security.serviceAuth.clients.agent.clientId=aiops-agent
  --set security.serviceAuth.clients.agent.clientSecret=agent-secret
  --set external.postgres.password=ci-postgres
  --set networkPolicy.enabled=true
  --set serviceMesh.istio.enabled=true
)

helm lint "$CHART_DIR" "${HELM_VALUES[@]}"
helm template "$RELEASE" "$CHART_DIR" "${HELM_VALUES[@]}" >"$RENDERED"

assert_contains() {
  local needle="$1"
  if ! grep -Fq "$needle" "$RENDERED"; then
    echo "Rendered chart is missing: $needle" >&2
    exit 1
  fi
}

resource_document() {
  local kind="$1"
  local name="$2"
  awk -v kind="$kind" -v name="$name" '
    BEGIN { RS = "---"; ORS = "" }
    $0 ~ "kind: " kind && $0 ~ "name: " name "([[:space:]]|$)" { print }
  ' "$RENDERED"
}

for component in server worker runner agent; do
  assert_contains "serviceAccountName: ${RELEASE}-aegisops-${component}"
done

assert_contains "name: ${RELEASE}-aegisops-server-auth"
assert_contains "name: ${RELEASE}-aegisops-worker-auth"
assert_contains "name: ${RELEASE}-aegisops-agent-auth"
assert_contains "name: ${RELEASE}-aegisops-agent-ingress"
assert_contains "kind: PeerAuthentication"
assert_contains "mode: STRICT"
assert_contains "name: ${RELEASE}-aegisops-agent"
assert_contains "cluster.local/ns/default/sa/${RELEASE}-aegisops-server"
assert_contains "cluster.local/ns/default/sa/${RELEASE}-aegisops-worker"
assert_contains "name: ${RELEASE}-aegisops-server"
assert_contains "cluster.local/ns/default/sa/${RELEASE}-aegisops-agent"
assert_contains "/internal/agent/*"

agent_deployment="$(resource_document Deployment "${RELEASE}-aegisops-agent")"
runner_deployment="$(resource_document Deployment "${RELEASE}-aegisops-runner")"

if grep -Fq "${RELEASE}-aegisops-secret" <<<"$agent_deployment"; then
  echo "Agent must not receive the shared database/runtime Secret." >&2
  exit 1
fi

if grep -Fq -- "-auth" <<<"$runner_deployment"; then
  echo "Runner must not receive Agent service credentials." >&2
  exit 1
fi

echo "Deployment security checks passed."

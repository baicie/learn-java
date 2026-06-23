#!/usr/bin/env bash
#
# Phase Z9 Zabbix MVP Demo Script
#
# Usage (Linux/macOS/Git Bash):
#   ./demo-zabbix-scenario.sh
#
# Environment variables:
#   AIOPS_BASE_URL                  - API base URL (default: http://localhost:8080)
#   AIOPS_ZABBIX_WEBHOOK_TOKEN      - Webhook token (default: dev-zabbix-webhook-token)
#   AIOPS_ZABBIX_DATASOURCE_ID      - Zabbix datasource ID (default: auto-created)
#
# Note: This script requires bash, curl, and jq.
# On Windows without WSL/Git Bash, use Node.js instead:
#   node scripts/demo-zabbix-scenario.mjs
#
# shellcheck disable=SC2086
set -euo pipefail

BASE_URL="${AIOPS_BASE_URL:-http://localhost:8080}"
DATASOURCE_ID="${AIOPS_ZABBIX_DATASOURCE_ID:-ds_zabbix_demo}"
WEBHOOK_TOKEN="${AIOPS_ZABBIX_WEBHOOK_TOKEN:-dev-zabbix-webhook-token}"
AUTH_TOKEN="${AIOPS_TOKEN:-}"

CURL_OPTS=(-sS)
if [[ -n "${AUTH_TOKEN}" ]]; then
  AUTH_HEADER=(-H "Authorization: Bearer ${AUTH_TOKEN}")
else
  AUTH_HEADER=()
fi

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

api_get() {
  local path="$1"
  curl "${CURL_OPTS[@]}" \
    "${AUTH_HEADER[@]}" \
    -H "X-Tenant-Id: ${TENANT_ID}" \
    "${BASE_URL}${path}"
}

api_post() {
  local path="$1"
  local body="${2:-{}}"

  curl "${CURL_OPTS[@]}" \
    "${AUTH_HEADER[@]}" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: ${TENANT_ID}" \
    -d "${body}" \
    "${BASE_URL}${path}"
}

webhook() {
  local event_id="$1"
  local trigger_id="$2"
  local title="$3"
  local severity="$4"
  local ts
  ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  curl "${CURL_OPTS[@]}" \
    -H "Content-Type: application/json" \
    -H "X-AegisOps-Webhook-Token: ${WEBHOOK_TOKEN}" \
    -H "X-Tenant-Id: ${TENANT_ID}" \
    -d "{
      \"datasourceId\": \"${DATASOURCE_ID}\",
      \"eventId\": \"${event_id}\",
      \"problemId\": \"${event_id}\",
      \"triggerId\": \"${trigger_id}\",
      \"objectId\": \"${trigger_id}\",
      \"status\": \"PROBLEM\",
      \"eventValue\": \"1\",
      \"severity\": \"${severity}\",
      \"title\": \"${title}\",
      \"message\": \"${title} on order-service\",
      \"hostId\": \"10084\",
      \"hostName\": \"aiops-demo-host\",
      \"app\": \"mall\",
      \"env\": \"demo\",
      \"service\": \"order-service\",
      \"endpoint\": \"/api/order/create\",
      \"startsAt\": \"${ts}\",
      \"tags\": {
        \"service\": \"order-service\",
        \"env\": \"demo\"
      }
    }" \
    "${BASE_URL}/api/integrations/zabbix/events?datasourceId=${DATASOURCE_ID}" >/tmp/aiops-webhook-${event_id}.json

  echo "Webhook ${title}: $(cat /tmp/aiops-webhook-${event_id}.json | jq -r '.message // .data.message // "ok"')"
}

latest_incident_id() {
  api_get "/api/incidents" | jq -r '
    .data.items[0].id //
    .data.content[0].id //
    .data[0].id //
    empty
  '
}

main() {
  need_cmd curl
  need_cmd jq
  need_cmd date

  echo "== Phase Z9 Zabbix MVP Demo =="
  echo "BASE_URL=${BASE_URL}"
  echo "DATASOURCE_ID=${DATASOURCE_ID}"

  echo
  echo "1. Ingest Zabbix webhook events"
  webhook "20001" "30001" "CPU High" "high"
  webhook "20002" "30002" "API Slow" "average"
  webhook "20003" "30003" "Health Check Failed" "disaster"
  webhook "20004" "30004" "Error Log Increased" "warning"

  echo
  echo "2. Aggregate Incident"
  api_post "/api/incidents/aggregate" '{
    "windowMinutes": 1440,
    "limit": 1000
  }' | tee /tmp/aiops-z9-aggregate.json | jq '.data'

  INCIDENT_ID="$(latest_incident_id)"
  if [[ -z "${INCIDENT_ID}" || "${INCIDENT_ID}" == "null" ]]; then
    echo "Failed to resolve latest incident id" >&2
    exit 1
  fi

  echo "INCIDENT_ID=${INCIDENT_ID}"

  echo
  echo "3. Collect Evidence"
  api_post "/api/incidents/${INCIDENT_ID}/evidence/zabbix/collect" '{
    "lookbackMinutes": 30
  }' | tee /tmp/aiops-z9-evidence.json | jq '.data'

  echo
  echo "4. Run RCA"
  api_post "/api/incidents/${INCIDENT_ID}/rca/analyze" '{
    "force": true
  }' | tee /tmp/aiops-z9-rca.json | jq '.data | {suspectedRootCause, confidence, matchedRules, evidenceRefs}'

  echo
  echo "5. Run AI Diagnosis"
  api_post "/api/incidents/${INCIDENT_ID}/ai/diagnose" '{
    "force": true,
    "locale": "zh-CN"
  }' | tee /tmp/aiops-z9-ai.json | jq '.data | {summary, rootCause, impact, evidenceRefs, matchedRules}'

  echo
  echo "6. Generate Markdown Report"
  api_post "/api/incidents/${INCIDENT_ID}/reports" '{
    "force": true,
    "locale": "zh-CN",
    "createdBy": "demo-zabbix-scenario"
  }' | tee /tmp/aiops-z9-report.json | jq '.data | {id, versionNo, title, createdAt}'

  echo
  echo "7. Markdown preview"
  jq -r '.data.markdownContent' /tmp/aiops-z9-report.json | sed -n '1,80p'

  echo
  echo "Z9 demo completed."
}

main "$@"

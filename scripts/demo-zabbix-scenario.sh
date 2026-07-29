#!/usr/bin/env bash
#
# Phase Z9 Zabbix MVP Demo Script
#
# Usage (Linux/macOS/Git Bash):
#   ./demo-zabbix-scenario.sh
#
# Environment variables:
#   AIOPS_BASE_URL        - API base URL (default: http://localhost:8080)
#   AIOPS_USERNAME        - AegisOps username (default: admin)
#   AIOPS_PASSWORD        - AegisOps password (default: admin123)
#   AIOPS_ZABBIX_ENDPOINT - Zabbix JSON-RPC URL (default: http://localhost:8081/api_jsonrpc.php)
#   AIOPS_ZABBIX_USERNAME - Zabbix username (default: Admin)
#   AIOPS_ZABBIX_PASSWORD - Zabbix password (default: zabbix)
#
# Note: This script requires bash, curl, and jq.
# On Windows without WSL/Git Bash, use Node.js instead:
#   node scripts/demo-zabbix-scenario.mjs
#
set -euo pipefail

BASE_URL="${AIOPS_BASE_URL:-http://localhost:8080}"
ADMIN_USERNAME="${AIOPS_USERNAME:-admin}"
ADMIN_PASSWORD="${AIOPS_PASSWORD:-admin123}"
ZABBIX_ENDPOINT="${AIOPS_ZABBIX_ENDPOINT:-http://localhost:8081/api_jsonrpc.php}"
ZABBIX_USERNAME="${AIOPS_ZABBIX_USERNAME:-Admin}"
ZABBIX_PASSWORD="${AIOPS_ZABBIX_PASSWORD:-zabbix}"
CONNECT_TIMEOUT_SECONDS="${AIOPS_CONNECT_TIMEOUT_SECONDS:-10}"
HTTP_TIMEOUT_SECONDS="${AIOPS_HTTP_TIMEOUT_SECONDS:-30}"
AUTH_TOKEN=""
TENANT_ID=""
DATASOURCE_ID=""
WEBHOOK_TOKEN=""
RUN_ID=""
EVENT_STARTED_AT=""
TMP_DIR=""
EVENT_IDS=()
EXPECTED_SOURCE_EVENT_IDS=()

CURL_OPTS=(
  -sS
  --fail-with-body
  --connect-timeout "${CONNECT_TIMEOUT_SECONDS}"
  --max-time "${HTTP_TIMEOUT_SECONDS}"
)
AUTH_HEADER=()

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

cleanup() {
  if [[ -n "${TMP_DIR}" && -d "${TMP_DIR}" ]]; then
    rm -f -- "${TMP_DIR}"/*.json
    rmdir -- "${TMP_DIR}"
  fi
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

login() {
  local payload response
  payload="$(jq -n \
    --arg username "${ADMIN_USERNAME}" \
    --arg password "${ADMIN_PASSWORD}" \
    '{username: $username, password: $password}')"
  response="$(curl "${CURL_OPTS[@]}" \
    -H "Content-Type: application/json" \
    -d "${payload}" \
    "${BASE_URL}/api/auth/login")"

  AUTH_TOKEN="$(jq -er '.data.token' <<<"${response}")"
  TENANT_ID="$(jq -er '.data.user.tenantId' <<<"${response}")"
  AUTH_HEADER=(-H "Authorization: Bearer ${AUTH_TOKEN}")
  echo "Logged in. Tenant: ${TENANT_ID}"
}

ensure_zabbix_datasource() {
  local response payload
  response="$(api_get "/api/datasources")"
  DATASOURCE_ID="$(jq -r --arg endpoint "${ZABBIX_ENDPOINT}" '
    if (.data | type) == "array" then
      (.data
        | map(select(.type == "zabbix" and .endpoint == $endpoint))
        | first | .id // empty)
    else
      ((.data.items // .data.content // [])
        | map(select(.type == "zabbix" and .endpoint == $endpoint))
        | first | .id // empty)
    end
  ' <<<"${response}")"

  if [[ -n "${DATASOURCE_ID}" ]]; then
    echo "Using existing Zabbix datasource: ${DATASOURCE_ID}"
    return
  fi

  payload="$(jq -n \
    --arg endpoint "${ZABBIX_ENDPOINT}" \
    --arg username "${ZABBIX_USERNAME}" \
    --arg password "${ZABBIX_PASSWORD}" \
    '{
      type: "zabbix",
      name: "Demo Zabbix",
      zabbix: {
        endpoint: $endpoint,
        username: $username,
        password: $password,
        connectTimeoutSeconds: 10,
        readTimeoutSeconds: 30
      }
    }')"
  response="$(api_post "/api/datasources" "${payload}")"
  DATASOURCE_ID="$(jq -er '.data.id' <<<"${response}")"
  echo "Created Zabbix datasource: ${DATASOURCE_ID}"
}

activate_zabbix_datasource() {
  local response
  response="$(api_post "/api/datasources/${DATASOURCE_ID}/test" '{}')"
  if ! jq -e '.data.ok == true' >/dev/null <<<"${response}"; then
    echo "Zabbix datasource connection test failed: ${response}" >&2
    exit 1
  fi
  echo "Zabbix datasource connection verified"
}

issue_webhook_token() {
  local response
  response="$(api_get "/api/datasources/${DATASOURCE_ID}/zabbix-webhook-token")"
  WEBHOOK_TOKEN="$(jq -er '.data.token' <<<"${response}")"
  if [[ "${WEBHOOK_TOKEN}" != zwh_* ]]; then
    echo "Server returned an invalid Zabbix webhook token" >&2
    exit 1
  fi
  echo "Datasource-scoped webhook token issued"
}

webhook() {
  local event_id="$1"
  local trigger_id="$2"
  local title="$3"
  local severity="$4"
  local ts="${EVENT_STARTED_AT}"
  local response_path="${TMP_DIR}/webhook-${event_id}.json"

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
    "${BASE_URL}/api/integrations/zabbix/events?datasourceId=${DATASOURCE_ID}" >"${response_path}"

  echo "Webhook ${title}: $(jq -r '.message // .data.message // "ok"' "${response_path}")"
}

incident_id_for_source_events() {
  local expected_source_ids response incident_ids incident_id alerts_response
  expected_source_ids="$(printf '%s\n' "$@" | jq -Rsc '
    split("\n") | map(select(length > 0))
  ')"
  response="$(api_get "/api/incidents")"
  incident_ids="$(jq -r '
    .data as $data
    | (if ($data | type) == "array" then
        $data
      else
        ($data.items // $data.content // [])
      end)
    | map(.id // empty)
    | .[]
  ' <<<"${response}")"

  while IFS= read -r incident_id; do
    [[ -n "${incident_id}" ]] || continue
    alerts_response="$(api_get "/api/incidents/${incident_id}/alerts")"
    if jq -e --argjson expected "${expected_source_ids}" '
      .data as $data
      | (if ($data | type) == "array" then
          $data
        else
          ($data.items // $data.content // [])
        end)
      | map(.sourceEventId) as $actual
      | all($expected[];
          . as $source_id | $actual | index($source_id) != null)
    ' >/dev/null <<<"${alerts_response}"; then
      printf '%s\n' "${incident_id}"
      return 0
    fi
  done <<<"${incident_ids}"

  return 1
}

main() {
  need_cmd curl
  need_cmd jq
  need_cmd date
  need_cmd mktemp
  need_cmd rm
  need_cmd rmdir

  umask 077
  TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aegisops-z9.XXXXXX")"
  trap cleanup EXIT

  local aggregate_path="${TMP_DIR}/aggregate.json"
  local evidence_path="${TMP_DIR}/evidence.json"
  local rca_path="${TMP_DIR}/rca.json"
  local ai_path="${TMP_DIR}/ai.json"
  local report_path="${TMP_DIR}/report.json"

  echo "== Phase Z9 Zabbix MVP Demo =="
  echo "BASE_URL=${BASE_URL}"

  echo
  echo "1. Login and prepare Zabbix datasource"
  login
  ensure_zabbix_datasource
  activate_zabbix_datasource
  issue_webhook_token
  echo "DATASOURCE_ID=${DATASOURCE_ID}"

  RUN_ID="$(date -u +"%Y%m%dT%H%M%S")-$$-${RANDOM}${RANDOM}"
  EVENT_STARTED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  EVENT_IDS=(
    "z9-${RUN_ID}-20001"
    "z9-${RUN_ID}-20002"
    "z9-${RUN_ID}-20003"
    "z9-${RUN_ID}-20004"
  )
  EXPECTED_SOURCE_EVENT_IDS=(
    "${DATASOURCE_ID}:${EVENT_IDS[0]}"
    "${DATASOURCE_ID}:${EVENT_IDS[1]}"
    "${DATASOURCE_ID}:${EVENT_IDS[2]}"
    "${DATASOURCE_ID}:${EVENT_IDS[3]}"
  )

  echo
  echo "2. Ingest Zabbix webhook events"
  webhook "${EVENT_IDS[0]}" "30001" "CPU High" "high"
  webhook "${EVENT_IDS[1]}" "30002" "API Slow" "average"
  webhook "${EVENT_IDS[2]}" "30003" "Health Check Failed" "disaster"
  webhook "${EVENT_IDS[3]}" "30004" "Error Log Increased" "warning"

  echo
  echo "3. Aggregate Incident"
  api_post "/api/incidents/aggregate" '{
    "windowMinutes": 1440,
    "limit": 1000
  }' >"${aggregate_path}"
  jq '.data' "${aggregate_path}"

  if ! INCIDENT_ID="$(incident_id_for_source_events "${EXPECTED_SOURCE_EVENT_IDS[@]}")"; then
    echo "No incident contains all injected Zabbix events" >&2
    exit 1
  fi

  echo "INCIDENT_ID=${INCIDENT_ID}"

  echo
  echo "4. Collect Evidence"
  api_post "/api/incidents/${INCIDENT_ID}/evidence/zabbix/collect" '{
    "lookbackMinutes": 30
  }' >"${evidence_path}"
  if ! jq -e '
    ((.data.evidenceCreated // 0) + (.data.evidenceUpdated // 0)) > 0
  ' "${evidence_path}" >/dev/null; then
    echo "Evidence collection returned no evidence" >&2
    exit 1
  fi
  jq '.data' "${evidence_path}"

  echo
  echo "5. Run RCA"
  api_post "/api/incidents/${INCIDENT_ID}/rca/analyze" '{
    "force": true
  }' >"${rca_path}"
  if ! jq -e '
    (.data.suspectedRootCause | type == "string" and length > 0)
    and (.data.matchedRules | type == "array" and length > 0)
  ' "${rca_path}" >/dev/null; then
    echo "RCA analysis returned no root cause or rules" >&2
    exit 1
  fi
  jq '.data | {suspectedRootCause, confidence, matchedRules, evidenceRefs}' "${rca_path}"

  echo
  echo "6. Run AI Diagnosis"
  api_post "/api/incidents/${INCIDENT_ID}/ai/diagnose" '{
    "force": true,
    "locale": "zh-CN"
  }' >"${ai_path}"
  if ! jq -e '
    (.data.summary | type == "string" and length > 0)
    and (.data.rootCause | type == "string" and length > 0)
    and (.data.impact | type == "string" and length > 0)
  ' "${ai_path}" >/dev/null; then
    echo "AI diagnosis returned incomplete content" >&2
    exit 1
  fi
  jq '.data | {summary, rootCause, impact, evidenceRefs, matchedRules}' "${ai_path}"

  echo
  echo "7. Generate Markdown Report"
  api_post "/api/incidents/${INCIDENT_ID}/reports" '{
    "force": true,
    "locale": "zh-CN",
    "createdBy": "demo-zabbix-scenario"
  }' >"${report_path}"
  if ! jq -e '
    (.data.id | type == "string" and length > 0)
    and (.data.markdownContent | type == "string"
      and contains("\u6545\u969c\u62a5\u544a")
      and contains("\u5173\u952e\u8bc1\u636e")
      and contains("AI \u8bca\u65ad"))
  ' "${report_path}" >/dev/null; then
    echo "Report markdown is missing required sections" >&2
    exit 1
  fi
  jq '.data | {id, versionNo, title, createdAt}' "${report_path}"

  echo
  echo "8. Markdown preview"
  jq -r '.data.markdownContent' "${report_path}" | sed -n '1,80p'

  echo
  echo "Z9 demo completed."
}

main "$@"

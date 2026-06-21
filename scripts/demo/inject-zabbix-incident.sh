#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${AIOPS_DEMO_ORDER_BASE_URL:-http://localhost:8088}"
ACTION="${1:-incident}"

request_json() {
  local method="$1"
  local path="$2"
  local body="${3:-}"

  if [[ -n "${body}" ]]; then
    curl -fsS -X "${method}" "${BASE_URL}${path}" \
      -H "Content-Type: application/json" \
      -d "${body}"
  else
    curl -fsS -X "${method}" "${BASE_URL}${path}"
  fi
}

case "${ACTION}" in
  incident)
    echo "Injecting full incident: CPU high + API slow + health down + error logs"
    request_json POST "/demo/fault/incident"
    echo
    ;;

  recover)
    echo "Recovering demo order-service"
    request_json POST "/demo/fault/recover"
    echo
    ;;

  reset)
    echo "Resetting demo order-service"
    request_json POST "/demo/fault/reset"
    echo
    ;;

  slow)
    echo "Injecting API slow fault"
    request_json POST "/demo/fault/apply" '{"slowApiEnabled":true,"slowApiDelayMs":2500}'
    echo
    ;;

  cpu)
    echo "Injecting CPU high fault"
    request_json POST "/demo/fault/apply" '{"cpuHighEnabled":true,"cpuWorkers":2}'
    echo
    ;;

  health-down)
    echo "Injecting health check failure"
    request_json POST "/demo/fault/apply" '{"healthy":false}'
    echo
    ;;

  error)
    echo "Injecting error log fault"
    request_json POST "/demo/fault/apply" '{"errorLogEnabled":true}'
    echo
    ;;

  state)
    request_json GET "/demo/fault/state"
    echo
    ;;

  *)
    cat <<EOF
Usage:
  $0 incident      Inject CPU high + API slow + health down + error logs
  $0 recover       Recover service but keep current error count
  $0 reset        Recover service and reset counters
  $0 slow         Enable slow API only
  $0 cpu          Enable CPU high only
  $0 health-down  Enable health failure only
  $0 error        Enable error log fault only
  $0 state        Show current fault state

Environment:
  AIOPS_DEMO_ORDER_BASE_URL=${BASE_URL}
EOF
    exit 1
    ;;
esac

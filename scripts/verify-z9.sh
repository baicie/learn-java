#!/usr/bin/env bash
# shellcheck disable=SC2086
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

echo "== Java module tests =="
mvn -pl modules/aiops-zabbix-adapter,modules/aiops-rca,apps/aiops-server -am test

echo
echo "== Python agent tests =="
if [[ -d "apps/aiops-agent" ]]; then
  (
    cd apps/aiops-agent
    pytest tests/test_phase_z9_mock_diagnosis.py
  )
else
  echo "apps/aiops-agent not found, skip"
fi

echo
echo "== Frontend tests =="
if [[ -d "web/console" ]]; then
  (
    cd web/console
    pnpm test
    pnpm build
  )
else
  echo "web/console not found, skip"
fi

echo
echo "== Server compile =="
mvn -pl apps/aiops-server -am compile

echo
echo "Phase Z9 verification passed."

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACTION="${1:-incident}"
if [[ $# -gt 0 ]]; then
  shift
fi

PYTHON_BIN="${PYTHON_BIN:-}"
if [[ -z "${PYTHON_BIN}" ]]; then
  if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
  elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
  else
    echo "Python is not found in PATH." >&2
    exit 1
  fi
fi

exec "${PYTHON_BIN}" "${SCRIPT_DIR}/setup-zabbix-demo.py" \
  --action "${ACTION}" \
  "$@"

#!/usr/bin/env bash
set -euo pipefail

cd apps/aiops-agent

PYTHON_BIN="${PYTHON_BIN:-}"
if [ -z "${PYTHON_BIN}" ]; then
  if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
  elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
  else
    echo "Python is not found in PATH." >&2
    exit 1
  fi
fi

"${PYTHON_BIN}" -m pip install -e '.[test]'
"${PYTHON_BIN}" -m ruff check src tests
"${PYTHON_BIN}" -m pytest -q

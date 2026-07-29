#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

if [ "${AIOPS_AGENT_SKIP_SCRIPT_TESTS:-0}" != "1" ]; then
  bash "$SCRIPT_DIR/test-agent-python-selection.sh"
fi

cd "$ROOT_DIR/apps/aiops-agent"

PYTHON_BIN="${PYTHON_BIN:-}"
if [ -z "${PYTHON_BIN}" ]; then
  for candidate in python3 python; do
    if command -v "$candidate" >/dev/null 2>&1 \
      && "$candidate" --version >/dev/null 2>&1; then
      PYTHON_BIN="$candidate"
      break
    fi
  done

  if [ -z "${PYTHON_BIN}" ]; then
    echo "Python is not found in PATH." >&2
    exit 1
  fi
fi

"${PYTHON_BIN}" -m pip install -e '.[test]'
"${PYTHON_BIN}" -m ruff check src tests
"${PYTHON_BIN}" -m pytest -q

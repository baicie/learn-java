#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/aegisops-agent-python.XXXXXX")"
FAKE_BIN="$TEST_ROOT/bin"
CALL_LOG="$TEST_ROOT/python-calls.log"

cleanup() {
  rm -f -- "$FAKE_BIN/python3" "$FAKE_BIN/python" "$CALL_LOG"
  rmdir -- "$FAKE_BIN" "$TEST_ROOT"
}
trap cleanup EXIT

mkdir "$FAKE_BIN"

printf '%s\n' '#!/usr/bin/env bash' 'exit 1' > "$FAKE_BIN/python3"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'if [ "${1:-}" = "--version" ]; then exit 0; fi' \
  'printf '\''%s\n'\'' "$*" >> "$AIOPS_AGENT_TEST_LOG"' \
  'exit 0' > "$FAKE_BIN/python"
chmod +x "$FAKE_BIN/python3" "$FAKE_BIN/python"

cd "$ROOT_DIR"

if ! PATH="$FAKE_BIN:$PATH" \
  PYTHON_BIN= \
  AIOPS_AGENT_SKIP_SCRIPT_TESTS=1 \
  AIOPS_AGENT_TEST_LOG="$CALL_LOG" \
  bash "$SCRIPT_DIR/agent.sh"; then
  echo "agent.sh did not fall back from an unusable python3 command." >&2
  exit 1
fi

EXPECTED_CALLS=$(cat <<'EOF'
-m pip install -e .[test]
-m ruff check src tests
-m pytest -q
EOF
)
ACTUAL_CALLS="$(cat "$CALL_LOG")"

if [ "$ACTUAL_CALLS" != "$EXPECTED_CALLS" ]; then
  echo "agent.sh invoked the fallback Python with unexpected arguments." >&2
  printf 'Expected:\n%s\nActual:\n%s\n' "$EXPECTED_CALLS" "$ACTUAL_CALLS" >&2
  exit 1
fi

echo "Agent Python selection guard passed."

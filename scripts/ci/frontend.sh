#!/usr/bin/env bash
set -euo pipefail

echo "==> Frontend CI"

PORTAL_DIR="web/portal"
CONSOLE_DIR="web/console"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Prefer system pnpm (CI restores it via setup-node cache). corepack may try
# to refetch a tarball offline and fail, so it's only a best-effort fallback.
if ! command -v pnpm > /dev/null 2>&1; then
  if command -v corepack > /dev/null 2>&1; then
    corepack enable || true
  fi
fi

# ---- Formily ADR guard (applies to portal only) ----
"$SCRIPT_DIR/check-formily-deps.sh"

# ---- Portal ----
run_frontend() {
  local dir="$1"
  if [ ! -f "${dir}/package.json" ]; then
    echo "Skip frontend: ${dir}/package.json not found."
    return 0
  fi

  cd "$ROOT_DIR/${dir}"

  if [ -f "pnpm-lock.yaml" ]; then
    pnpm install --frozen-lockfile
  else
    pnpm install
  fi

  has_script() {
    node -e "const p=require('./package.json'); process.exit(p.scripts && p.scripts['$1'] ? 0 : 1)"
  }

  run_script() {
    local name="$1"
    if has_script "${name}"; then
      echo "[${dir}] pnpm run ${name}"
      pnpm run "${name}"
    else
      echo "Skip ${dir}/${name}: script not found."
    fi
  }

  # AGENTS.md §21.1 顺序：format → lint → typecheck → test → build
  run_script "format:check"
  run_script "lint"
  run_script "typecheck"
  run_script "test"
  run_script "build"
}

run_frontend "$PORTAL_DIR"
run_frontend "$CONSOLE_DIR"

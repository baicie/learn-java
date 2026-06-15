#!/usr/bin/env bash
set -euo pipefail

echo "==> Frontend CI"

FRONTEND_DIR="web/console"

if [ ! -f "${FRONTEND_DIR}/package.json" ]; then
  echo "Skip frontend: ${FRONTEND_DIR}/package.json not found."
  exit 0
fi

cd "${FRONTEND_DIR}"

# Prefer system pnpm (CI restores it via setup-node cache). corepack may try
# to refetch a tarball offline and fail, so it's only a best-effort fallback.
if ! command -v pnpm > /dev/null 2>&1; then
  if command -v corepack > /dev/null 2>&1; then
    corepack enable || true
  fi
fi

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
    pnpm run "${name}"
  else
    echo "Skip ${name}: script not found."
  fi
}

# AGENTS.md §21.1 顺序：format → lint → typecheck → test → build
# format 放最前是为了让 lint 不被格式问题污染输出。
run_script "format:check"
run_script "lint"
run_script "typecheck"
run_script "test"
run_script "build"

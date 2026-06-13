#!/usr/bin/env bash
set -euo pipefail

echo "==> Frontend CI"

FRONTEND_DIR="web/console"

if [ ! -f "${FRONTEND_DIR}/package.json" ]; then
  echo "Skip frontend: ${FRONTEND_DIR}/package.json not found."
  exit 0
fi

cd "${FRONTEND_DIR}"

if command -v corepack > /dev/null 2>&1; then
  corepack enable
fi

if [ -f "pnpm-lock.yaml" ]; then
  pnpm install --frozen-lockfile
else
  pnpm install
fi

has_script() {
  node -e "const p=require('./package.json'); process.exit(p.scripts && p.scripts['$1'] ? 0 : 1)"
}

if has_script "typecheck"; then
  pnpm run typecheck
else
  echo "Skip typecheck: script not found."
fi

if has_script "lint"; then
  pnpm run lint
else
  echo "Skip lint: script not found."
fi

if has_script "test"; then
  pnpm run test
else
  echo "Skip test: script not found."
fi

if has_script "build"; then
  pnpm run build
else
  echo "Skip build: script not found."
fi

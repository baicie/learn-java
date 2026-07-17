#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$ROOT_DIR"

if ! command -v pnpm >/dev/null 2>&1; then
  if command -v corepack >/dev/null 2>&1; then
    corepack enable || true
  fi
fi

pnpm install --frozen-lockfile --prefer-offline
pnpm exec tsx scripts/ci/docs.ts
pnpm exec tsx scripts/docs.ts check
bash scripts/ci/test-workflow-resource-policy.sh

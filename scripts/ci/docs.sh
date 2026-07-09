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

pnpm install --frozen-lockfile
pnpm exec tsx scripts/ci/docs.ts
pnpm exec tsx scripts/ci/check-record-phase0-contracts.ts
pnpm exec tsx scripts/ci/check-record-phase1-baseline.ts
pnpm exec tsx scripts/ci/check-record-phase3-model.ts
pnpm exec tsx scripts/docs.ts check

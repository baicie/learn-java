#!/usr/bin/env bash
# Docs CI - validates docs structure, work-record Phase 0 contracts, and frontmatter.
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
pnpm exec tsx scripts/docs.ts check

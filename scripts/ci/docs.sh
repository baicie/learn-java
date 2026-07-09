#!/usr/bin/env bash
# Docs CI - delegates to scripts/ci/docs.ts
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if ! command -v pnpm >/dev/null 2>&1; then
  if command -v corepack >/dev/null 2>&1; then
    corepack enable || true
  fi
fi

pnpm install --frozen-lockfile
pnpm exec tsx scripts/ci/docs.ts

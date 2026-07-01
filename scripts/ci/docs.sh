#!/usr/bin/env bash
set -euo pipefail

if ! command -v pnpm >/dev/null 2>&1; then
  if command -v corepack >/dev/null 2>&1; then
    corepack enable || true
  fi
fi

pnpm install --frozen-lockfile
pnpm exec tsx scripts/ci/docs.ts
pnpm exec tsx scripts/docs.ts check

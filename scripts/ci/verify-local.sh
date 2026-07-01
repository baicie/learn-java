#!/usr/bin/env bash
set -euo pipefail

pnpm exec tsx scripts/ci/docs.ts
bash scripts/ci/backend.sh
bash scripts/ci/frontend.sh
bash scripts/ci/agent.sh

#!/usr/bin/env bash
set -euo pipefail

echo "==> Local verification"
echo ""

bash scripts/ci/docs.sh
echo ""

bash scripts/ci/backend.sh
echo ""

bash scripts/ci/frontend.sh
echo ""

echo "==> Local verification passed."

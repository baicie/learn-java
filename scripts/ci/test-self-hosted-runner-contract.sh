#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

fail() {
  echo "self-hosted runner contract failed: $*" >&2
  exit 1
}

workflow_files=(.github/workflows/*.yml)

if grep -n 'runs-on: ubuntu-latest' "${workflow_files[@]}"; then
  fail "all jobs must route to the aegisops-cn self-hosted runner"
fi

for workflow in "${workflow_files[@]}"; do
  if grep -q 'runs-on:' "$workflow" && ! grep -q 'aegisops-cn' "$workflow"; then
    fail "$workflow does not declare the aegisops-cn label"
  fi
done

if grep -nE 'actions/setup-(java|node|python)|corepack prepare|apt-get install' \
  .github/workflows/*.yml; then
  fail "self-hosted workflows must use the preinstalled toolchain"
fi

test -x scripts/ci/self-hosted-runner-preflight.sh \
  || fail "runner preflight script is missing or not executable"

grep -q 'AIOPS_CI_PREINSTALLED_BROWSER' scripts/ci/frontend.sh \
  || fail "frontend CI still downloads Playwright browsers on every run"

if grep -q 'docker/setup-buildx-action' .github/workflows/*.yml; then
  fail "workflows must reuse the host-installed Buildx plugin"
fi

if grep -q 'type=gha' .github/workflows/release-verify.yml; then
  fail "release verification still transfers BuildKit cache through GitHub"
fi

test -f docs/operations/self-hosted-runner-cn.md \
  || fail "runner operations guide is missing"

grep -q 'test-self-hosted-runner-contract.sh' scripts/ci/release-preflight.sh \
  || fail "release preflight does not enforce the runner contract"

bash scripts/ci/test-self-hosted-runner-preflight.sh

echo "Self-hosted runner contract passed."

#!/usr/bin/env bash
set -euo pipefail

required_vars=(
  E2E_API_BASE_URL
  E2E_PORTAL_BASE_URL
  E2E_ADMIN_USERNAME
  E2E_ADMIN_PASSWORD
  E2E_USER_A_USERNAME
  E2E_USER_A_PASSWORD
  E2E_USER_B_USERNAME
  E2E_USER_B_PASSWORD
)

for name in "${required_vars[@]}"; do
  if [ -z "${!name:-}" ]; then
    echo "${name} is required." >&2
    exit 1
  fi
done

corepack enable

pnpm install --frozen-lockfile
pnpm -C web/portal run e2e:install
pnpm -C web/portal run e2e

#!/usr/bin/env bash
set -euo pipefail

echo "==> Frontend CI"

PORTAL_DIR="web/portal"
CONSOLE_DIR="web/console"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Prefer system pnpm (CI restores it via setup-node cache). corepack may try
# to refetch a tarball offline and fail, so it's only a best-effort fallback.
if ! command -v pnpm > /dev/null 2>&1; then
  if command -v corepack > /dev/null 2>&1; then
    corepack enable || true
  fi
fi

# ---- Formily ADR guard (applies to portal only) ----
"$SCRIPT_DIR/check-formily-deps.sh"
node "$SCRIPT_DIR/check-portal-no-features.mjs"
node "$SCRIPT_DIR/check-portal-ui-primitives.mjs"

run_frontend() {
  local dir="$1"
  local abs_dir="$ROOT_DIR/$dir"

  if [ ! -f "$abs_dir/package.json" ]; then
    echo "Skip frontend: ${dir}/package.json not found."
    return 0
  fi

  cd "$abs_dir"

  if [ -f "pnpm-lock.yaml" ]; then
    pnpm install --frozen-lockfile --prefer-offline
  else
    pnpm install --prefer-offline
  fi

  has_script() {
    node -e "const p=require('./package.json'); process.exit(p.scripts && p.scripts['$1'] ? 0 : 1)"
  }

  run_script() {
    local name="$1"
    if has_script "${name}"; then
      echo "[${dir}] pnpm run ${name}"
      pnpm run "${name}"
    else
      echo "Skip ${dir}/${name}: script not found."
    fi
  }

  # AGENTS.md §21.1 顺序：format → lint → typecheck → test → build
  run_script "format:check"
  run_script "lint"
  run_script "typecheck"

  if has_script "test:browser:install"; then
    local playwright_version browser_marker browser_ready location_count
    playwright_version="$(pnpm exec playwright --version | awk '{print $2}')"
    browser_marker="${XDG_CACHE_HOME:-$HOME/.cache}/aegisops/playwright-${playwright_version}-chromium-ready"
    browser_ready=true
    location_count=0

    while IFS= read -r location; do
      location_count=$((location_count + 1))
      if [ ! -d "$location" ]; then
        browser_ready=false
      fi
    done < <(
      pnpm exec playwright install --dry-run chromium \
        | sed -n 's/^[[:space:]]*Install location:[[:space:]]*//p'
    )

    if [ -f "$browser_marker" ] && [ "$location_count" -gt 0 ] && [ "$browser_ready" = true ]; then
      echo "[${dir}] Reuse Playwright ${playwright_version} Chromium"
    else
      run_script "test:browser:install"
      mkdir -p "$(dirname "$browser_marker")"
      touch "$browser_marker"
    fi
  fi

  if has_script "test:coverage"; then
    run_script "test:coverage"
  else
    run_script "test"
  fi

  run_script "build"
}

run_frontend "$PORTAL_DIR"

if [ "${RUN_LEGACY_CONSOLE_CI:-0}" = "1" ]; then
  echo "RUN_LEGACY_CONSOLE_CI=1, running legacy console CI."
  run_frontend "$CONSOLE_DIR"
else
  echo "Legacy console CI is disabled by default. Set RUN_LEGACY_CONSOLE_CI=1 to run web/console."
fi

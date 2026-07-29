#!/usr/bin/env bash
# scripts/ci/check-formily-deps.sh
# Validates that web/portal does not use forbidden Formily UI packages.
# See ADR-0005: docs/adr/0005-work-record-designer-formily-core-only.md
set -euo pipefail

FORBIDDEN=(
  "@formily/antd"
  "@formily/antd-icons"
  "@formily/antd-setters"
  "@formily/antd-x"
  "@formily/arco"
  "@formily/arco-setters"
  "@formily/next"
  "@formily/next-setters"
  "@formily/element-plus"
  "@formily/element-plus-setters"
  "@formily/fusion"
  "@formily/fusion-setters"
  "@formily/fusion-scoped"
  "@formily/next-scoped"
  "@formily/vant"
  "@formily/vant-setters"
  "@formily/naive"
  "@formily/naive-setters"
  "@formily/primevue"
  "@formily/designable-setters"
  "@formily/designable-formily"
  "@formily/react-formily"
  "@formily/antd-component-playground"
)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
PACKAGE_JSON="$ROOT_DIR/web/portal/package.json"
LOCK_FILE="$ROOT_DIR/web/portal/pnpm-lock.yaml"

if [[ ! -f "$PACKAGE_JSON" ]]; then
  echo "ERROR: $PACKAGE_JSON not found" >&2
  exit 1
fi

echo "--- Checking Formily dependency compliance (ADR-0005) ---"

FOUND=0

# Check package.json dependencies
while IFS= read -r pkg_name; do
  [[ -z "$pkg_name" ]] && continue
  for forbidden in "${FORBIDDEN[@]}"; do
    if [[ "$pkg_name" == "$forbidden" ]]; then
      echo "FORBIDDEN: $pkg_name found in $PACKAGE_JSON" >&2
      FOUND=1
    fi
  done
done < <(
  node -e "
    const fs = require('fs')
    const pkg = JSON.parse(fs.readFileSync(process.argv[1], 'utf8'))
    const deps = {
      ...(pkg.dependencies || {}),
      ...(pkg.devDependencies || {}),
      ...(pkg.optionalDependencies || {}),
      ...(pkg.peerDependencies || {})
    }
    Object.keys(deps).forEach((name) => console.log(name))
  " "$PACKAGE_JSON"
)

# Check pnpm-lock.yaml
if [[ -f "$LOCK_FILE" ]]; then
  while IFS= read -r pkg_name; do
    [[ -z "$pkg_name" ]] && continue
    for forbidden in "${FORBIDDEN[@]}"; do
      if [[ "$pkg_name" == "$forbidden" ]]; then
        echo "FORBIDDEN: $pkg_name found in $LOCK_FILE" >&2
        FOUND=1
      fi
    done
  done < <(
    node -e "
      const fs = require('fs')
      const content = fs.readFileSync(process.argv[1], 'utf8')
      const seen = new Set()
      const re = /@formily\/[A-Za-z0-9._-]+/g
      for (const match of content.matchAll(re)) {
        seen.add(match[0])
      }
      Array.from(seen).sort().forEach((name) => console.log(name))
    " "$LOCK_FILE"
  )
fi

if [[ $FOUND -eq 1 ]]; then
  echo "" >&2
  echo "FATAL: Forbidden Formily UI packages detected." >&2
  echo "Portal must only use @formily/core, @formily/react, @formily/json-schema, @formily/validator." >&2
  echo "Do NOT introduce @formily/antd*, @formily/designable-setters, @formily/fusion*, etc." >&2
  echo "See ADR-0005: docs/adr/0005-work-record-designer-formily-core-only.md" >&2
  exit 1
fi

echo "PASS: No forbidden Formily packages found in web/portal."

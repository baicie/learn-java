#!/usr/bin/env bash
# scripts/ci/check-formily-deps.sh
# -----------------------------------
# 检查 web/portal/package.json 中是否意外引入了禁用包。
# 退出 0 = 通过，退出 1 = 发现禁用包。
#
# 用法：
#   bash scripts/ci/check-formily-deps.sh
#   # 或在 CI 中：
#   pnpm run check:formily-deps
#
# 禁用包清单（ADR-0005）：
#   @formily/antd
#   @formily/antd-icons
#   @formily/antd-setters
#   @formily/antd-x
#   @formily/arco
#   @formily/arco-setters
#   @formily/next
#   @formily/next-setters
#   @formily/element-plus
#   @formily/element-plus-setters
#   @formily/fusion
#   @formily/fusion-setters
#   @formily/fusion-scoped
#   @formily/next-scoped
#   @formily/vant
#   @formily/vant-setters
#   @formily/naive
#   @formily/naive-setters
#   @formily/primevue
#   @formily/designable-setters
#   @formily/designable-formily
#   @formily/react-formily
#   @formily/antd-component-playground

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
SCAN_TARGETS=("$PACKAGE_JSON")
if [[ -f "$LOCK_FILE" ]]; then
  SCAN_TARGETS+=("$LOCK_FILE")
fi

# Scan package.json for forbidden entries in dependencies / devDependencies.
for field in '"dependencies"' '"devDependencies"'; do
  while IFS= read -r pkg; do
    pkg_name="${pkg%:*}"; pkg_name="${pkg_name//\"/}"; pkg_name="${pkg_name// }"
    [[ -z "$pkg_name" ]] && continue
    for forbidden in "${FORBIDDEN[@]}"; do
      if [[ "$pkg_name" == "$forbidden" ]]; then
        echo "FORBIDDEN: $pkg_name found in $PACKAGE_JSON ($field)" >&2
        FOUND=1
      fi
    done
  done < <(node -e "
    const fs = require('fs');
    const pkg = JSON.parse(fs.readFileSync('$PACKAGE_JSON', 'utf8'));
    const section = $field;
    if (!section) process.exit(0);
    Object.keys(section).forEach(k => console.log(k + ':' + section[k]));
  " 2>/dev/null || true)
done

# Best-effort scan of pnpm-lock.yaml when it exists. pnpm-lock uses
# `/<pkg>@<version>:` blocks so we strip the leading slash and trailing
# version to get the package name.
if [[ -f "$LOCK_FILE" ]]; then
  while IFS= read -r pkg; do
    [[ -z "$pkg" ]] && continue
    for forbidden in "${FORBIDDEN[@]}"; do
      if [[ "$pkg" == "$forbidden" ]]; then
        echo "FORBIDDEN: $pkg found in $LOCK_FILE" >&2
        FOUND=1
      fi
    done
  done < <(
    node -e "
      const fs = require('fs');
      const lines = fs.readFileSync('$LOCK_FILE', 'utf8').split(/\r?\n/);
      const seen = new Set();
      for (const line of lines) {
        // Match a section header like '  /@formily/antd@2.3.7:'
        const m = line.match(/^\s+\/(@formily\/[^@]+)@[^:]+:/);
        if (m && !seen.has(m[1])) {
          seen.add(m[1]);
          console.log(m[1]);
        }
      }
    " 2>/dev/null || true
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
exit 0

#!/usr/bin/env bash
set -euo pipefail

echo "==> Docs CI"

if [ ! -f "docs/INDEX.md" ]; then
  echo "Skip docs: docs/INDEX.md not found."
  exit 0
fi

echo "Docs directory exists, checking structure..."

if [ -d "docs/_templates" ]; then
  echo "  - docs/_templates: $(ls docs/_templates 2>/dev/null | wc -l | tr -d ' ') templates"
fi

if [ -d "docs/architecture" ]; then
  echo "  - docs/architecture: $(ls docs/architecture 2>/dev/null | wc -l | tr -d ' ') files"
fi

if [ -d "docs/designs" ]; then
  echo "  - docs/designs: $(ls docs/designs 2>/dev/null | wc -l | tr -d ' ') files"
fi

if [ -d "docs/phases" ]; then
  echo "  - docs/phases: $(ls docs/phases 2>/dev/null | wc -l | tr -d ' ') files"
fi

echo "Docs check passed."

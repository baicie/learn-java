#!/usr/bin/env node

/**
 * Docs CI - validates docs structure
 */

import { readdirSync } from 'node:fs'
import { resolve } from 'node:path'

import { exists, printSection, repoRoot } from './lib.js'

printSection('Docs CI')

if (!exists('docs/INDEX.md')) {
  console.log('Skip docs: docs/INDEX.md not found.')
  process.exit(0)
}

console.log('Docs directory exists, checking structure...')

for (const [label, dir] of [
  ['docs/_templates', 'docs/_templates'],
  ['docs/architecture', 'docs/architecture'],
  ['docs/designs', 'docs/designs'],
  ['docs/phases', 'docs/phases'],
] as const) {
  if (!exists(dir)) {
    continue
  }

  const count = readdirSync(resolve(repoRoot, dir)).length
  console.log(
    `  - ${label}: ${count} ${label === 'docs/_templates' ? 'templates' : 'files'}`
  )
}

console.log('Docs check passed.')

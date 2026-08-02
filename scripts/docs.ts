#!/usr/bin/env node

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const root = path.join(__dirname, '..')
const docsRoot = path.join(root, 'docs')

const VALID_TYPES = new Set([
  'architecture',
  'adr',
  'phase',
  'design',
  'review',
  'fix',
  'api',
  'database',
  'integration',
  'ai',
  'operation',
  'runbook',
  'research',
])

const VALID_STATUS = new Set(['draft', 'review', 'accepted', 'deprecated'])

const TYPE_DIRS: Record<string, string> = {
  architecture: 'architecture',
  adr: 'adr',
  phase: 'phases',
  design: 'designs',
  review: 'reviews',
  fix: 'fixes',
  api: 'api',
  database: 'database',
  integration: 'integrations',
  ai: 'ai',
  operation: 'operations',
  runbooks: 'runbooks',
  research: 'research',
}

function main(): void {
  const [command, ...args] = process.argv.slice(2)

  switch (command) {
    case 'init':
      initDocs()
      break
    case 'new':
      newDoc(args)
      break
    case 'check':
      checkDocs()
      break
    case 'index':
      generateIndex()
      break
    case 'help':
    case undefined:
      printHelp()
      break
    default:
      fail(`Unknown command: ${command}`)
  }
}

function printHelp(): void {
  console.log(`
AegisOps docs tool

Usage:
  npx tsx scripts/docs.ts init
  npx tsx scripts/docs.ts new <type> <slug> --title "Title" [--phase phase-0] [--status draft]
  npx tsx scripts/docs.ts check
  npx tsx scripts/docs.ts index

Examples:
  npx tsx scripts/docs.ts init
  npx tsx scripts/docs.ts new design project-foundation --title "Project Foundation Design" --phase phase-0
  npx tsx scripts/docs.ts new adr use-java-spring-boot --title "Use Java Spring Boot"
  npx tsx scripts/docs.ts new fix zabbix-auth-failed --title "Fix Zabbix Auth Failed" --phase phase-1
  npx tsx scripts/docs.ts check
  npx tsx scripts/docs.ts index
`)
}

function initDocs(): void {
  const dirs = [
    'architecture',
    'adr',
    'phases/phase-0',
    'phases/phase-1',
    'phases/phase-2',
    'phases/phase-3',
    'phases/phase-4',
    'phases/phase-5',
    'phases/phase-6',
    'designs/phase-0',
    'designs/phase-1',
    'designs/phase-2',
    'designs/phase-3',
    'designs/phase-4',
    'designs/phase-5',
    'designs/phase-6',
    'reviews/phase-0',
    'reviews/phase-1',
    'reviews/phase-2',
    'reviews/phase-3',
    'reviews/phase-4',
    'reviews/phase-5',
    'reviews/phase-6',
    'fixes/phase-0',
    'fixes/phase-1',
    'fixes/phase-2',
    'fixes/phase-3',
    'fixes/phase-4',
    'fixes/phase-5',
    'fixes/phase-6',
    'api',
    'database',
    'integrations',
    'ai',
    'operations',
    'runbooks',
    'research',
    '_templates',
  ]

  for (const dir of dirs) {
    ensureDir(path.join(docsRoot, dir))
  }

  writeFileIfAbsent(path.join(docsRoot, 'README.md'), docsReadme())
  writeFileIfAbsent(
    path.join(docsRoot, '_templates', 'design.md'),
    templateDesign()
  )
  writeFileIfAbsent(path.join(docsRoot, '_templates', 'adr.md'), templateAdr())
  writeFileIfAbsent(
    path.join(docsRoot, '_templates', 'phase.md'),
    templatePhase()
  )
  writeFileIfAbsent(
    path.join(docsRoot, '_templates', 'review.md'),
    templateReview()
  )
  writeFileIfAbsent(path.join(docsRoot, '_templates', 'fix.md'), templateFix())
  writeFileIfAbsent(
    path.join(docsRoot, '_templates', 'runbook.md'),
    templateRunbook()
  )
  writeFileIfAbsent(
    path.join(docsRoot, '_templates', 'research.md'),
    templateResearch()
  )

  writeFileIfAbsent(
    path.join(docsRoot, 'architecture', 'system-overview.md'),
    starterDoc({
      title: 'System Overview',
      type: 'architecture',
      status: 'draft',
      phase: 'global',
      body: `# System Overview

## Purpose

This document describes the high-level architecture of AegisOps / FaultLens.

## Core Flow

\`\`\`txt
Zabbix alert
  ↓
AlertEvent
  ↓
Incident
  ↓
RCA evidence
  ↓
AI diagnosis
  ↓
Runbook
  ↓
Approved automation
  ↓
Postmortem
\`\`\`

## Applications

- aegisops-app (apps/aiops-server + modules/aiops-worker-runtime)
- aiops-agent
- aiops-runner

## Data Stores

- PostgreSQL
- Redis
- VictoriaMetrics
- ClickHouse
- MinIO
`,
    })
  )

  writeFileIfAbsent(
    path.join(docsRoot, 'phases', 'phase-0', 'README.md'),
    starterDoc({
      title: 'Phase 0 Foundation',
      type: 'phase',
      status: 'draft',
      phase: 'phase-0',
      body: `# Phase 0 Foundation

## Goal

Build the project foundation.

## Deliverables

- Maven multi-module project
- aegisops-app
- aiops-agent
- aiops-runner
- React console
- Docker Compose
- PostgreSQL / Redis / ClickHouse / VictoriaMetrics / MinIO
- Flyway
- Spring Security + JWT
- RBAC
- OpenAPI
- Health checks

## Acceptance

- Local environment starts successfully.
- User can log in.
- API docs are accessible.
- Empty dashboard renders.
`,
    })
  )

  generateIndex()
  console.log('Docs structure initialized.')
}

function newDoc(args: string[]): void {
  const [type, slug] = args
  const flags = parseFlags(args.slice(2))

  if (!type || !slug) {
    fail(
      'Usage: npx tsx scripts/docs.ts new <type> <slug> --title "Title" [--phase phase-0]'
    )
  }

  if (!VALID_TYPES.has(type)) {
    fail(
      `Invalid type "${type}". Valid types: ${Array.from(VALID_TYPES).join(', ')}`
    )
  }

  const title = (flags['title'] as string) || toTitle(slug)
  const phase = (flags['phase'] as string) || defaultPhaseForType(type)
  const status = (flags['status'] as string) || 'draft'

  if (!VALID_STATUS.has(status)) {
    fail(
      `Invalid status "${status}". Valid status: ${Array.from(VALID_STATUS).join(', ')}`
    )
  }

  const safeSlug = normalizeSlug(slug)
  const date = today()
  const baseDir = resolveDocDir(type, phase)

  ensureDir(baseDir)

  const filename =
    type === 'adr'
      ? `${nextAdrNumber()}-${safeSlug}.md`
      : type === 'phase'
        ? 'README.md'
        : `${date}-${safeSlug}.md`

  const filepath = path.join(baseDir, filename)

  if (fs.existsSync(filepath)) {
    fail(`File already exists: ${relative(filepath)}`)
  }

  const content = renderDocByType({
    type,
    title,
    status,
    phase,
    slug: safeSlug,
  })
  fs.writeFileSync(filepath, content, 'utf8')

  console.log(`Created ${relative(filepath)}`)
}

function checkDocs(): void {
  const files = listMarkdownFiles(docsRoot).filter(
    (file) => !file.includes(`${path.sep}_templates${path.sep}`)
  )

  let errors = 0
  let warnings = 0

  for (const file of files) {
    const rel = relative(file)
    const content = fs.readFileSync(file, 'utf8')
    const frontmatter = parseFrontmatter(content)

    if (!frontmatter) {
      error(`${rel}: missing frontmatter`)
      errors++
      continue
    }

    for (const field of ['title', 'type', 'status', 'created', 'updated']) {
      if (!frontmatter[field]) {
        error(`${rel}: missing frontmatter field "${field}"`)
        errors++
      }
    }

    if (frontmatter.type && !VALID_TYPES.has(frontmatter.type)) {
      error(`${rel}: invalid type "${frontmatter.type}"`)
      errors++
    }

    if (frontmatter.status && !VALID_STATUS.has(frontmatter.status)) {
      error(`${rel}: invalid status "${frontmatter.status}"`)
      errors++
    }

    if (!/^#\s+/.test(content.replace(/^---[\s\S]*?---\s*/, ''))) {
      error(`${rel}: missing H1 title`)
      errors++
    }

    const filename = path.basename(file)
    if (/\s/.test(filename)) {
      error(`${rel}: filename contains spaces`)
      errors++
    }

    const KNOWN_UPPERCASE = new Set([
      'README.md',
      'INDEX.md',
      'CHANGELOG.md',
      'LICENSE',
    ])
    if (filename !== filename.toLowerCase() && !KNOWN_UPPERCASE.has(filename)) {
      warn(`${rel}: filename should be lowercase`)
      warnings++
    }

    if (frontmatter.status === 'accepted' && content.includes('TODO')) {
      warn(`${rel}: accepted document still contains TODO`)
      warnings++
    }
  }

  console.log(`Checked ${files.length} markdown files.`)
  console.log(`Errors: ${errors}`)
  console.log(`Warnings: ${warnings}`)

  if (errors > 0) {
    process.exit(1)
  }
}

function generateIndex(): void {
  ensureDir(docsRoot)

  const files = listMarkdownFiles(docsRoot)
    .filter((file) => !file.endsWith(`${path.sep}INDEX.md`))
    .filter((file) => !file.includes(`${path.sep}_templates${path.sep}`))
    .sort()

  const groups = new Map<
    string,
    Array<{
      rel: string
      title: string
      status: string
      phase: string
      updated: string
    }>
  >()

  for (const file of files) {
    const rel = relative(file)
    const content = fs.readFileSync(file, 'utf8')
    const fm = parseFrontmatter(content)
    const type = fm?.type || 'unknown'

    if (!groups.has(type)) {
      groups.set(type, [])
    }

    groups.get(type)!.push({
      rel,
      title: fm?.title || path.basename(file),
      status: fm?.status || 'unknown',
      phase: fm?.phase || '',
      updated: fm?.updated || '',
    })
  }

  const statusOrder = new Map([
    ['accepted', 0],
    ['review', 1],
    ['draft', 2],
    ['deprecated', 3],
  ])
  for (const items of groups.values()) {
    items.sort(
      (left, right) =>
        (statusOrder.get(left.status) ?? 4) -
          (statusOrder.get(right.status) ?? 4) ||
        left.rel.localeCompare(right.rel)
    )
  }

  let out = `---
title: Documentation Index
type: operation
status: accepted
phase: global
owner: script
created: ${today()}
updated: ${today()}
related: []
---

# Documentation Index

Start with [docs/README.md](README.md). Accepted documents are listed first;
deprecated documents are retained only for historical context.

This file is generated by:

\`\`\`bash
npx tsx scripts/docs.ts index
\`\`\`

Do not edit it manually.

`

  for (const [type, items] of groups.entries()) {
    out += `## ${type}\n\n`
    out += `| Title | Status | Phase | Updated | Path |\n`
    out += `|---|---|---|---|---|\n`

    for (const item of items) {
      out += `| ${escapeTable(item.title)} | ${item.status} | ${item.phase} | ${item.updated} | [${item.rel}](${toMarkdownLink(item.rel)}) |\n`
    }

    out += `\n`
  }

  fs.writeFileSync(
    path.join(docsRoot, 'INDEX.md'),
    `${out.trimEnd()}\n`,
    'utf8'
  )
  console.log('Generated docs/INDEX.md')
}

function resolveDocDir(type: string, phase: string): string {
  const base = TYPE_DIRS[type]

  if (!base) {
    fail(`Unknown type: ${type}`)
  }

  if (['design', 'review', 'fix'].includes(type)) {
    return path.join(docsRoot, base, phase || 'phase-0')
  }

  if (type === 'phase') {
    return path.join(docsRoot, base, phase || 'phase-0')
  }

  return path.join(docsRoot, base)
}

interface DocInput {
  type: string
  title: string
  status: string
  phase: string
  slug?: string
}

function renderDocByType(input: DocInput): string {
  switch (input.type) {
    case 'adr':
      return renderAdr(input)
    case 'phase':
      return renderPhase(input)
    case 'design':
      return renderDesign(input)
    case 'review':
      return renderReview(input)
    case 'fix':
      return renderFix(input)
    case 'runbook':
      return renderRunbook(input)
    case 'research':
      return renderResearch(input)
    default:
      return starterDoc({
        title: input.title,
        type: input.type,
        status: input.status,
        phase: input.phase,
        body: `# ${input.title}

## Background

## Goals

## Details

## Risks

## Related Documents
`,
      })
  }
}

function renderFrontmatter(opts: {
  title: string
  type: string
  status: string
  phase: string
}): string {
  return `---
title: ${opts.title}
type: ${opts.type}
status: ${opts.status}
phase: ${opts.phase}
owner: ai
created: ${today()}
updated: ${today()}
related: []
---

`
}

function renderDesign(input: DocInput): string {
  return `${renderFrontmatter(input)}# ${input.title}

## 1. Scope

## 2. Background

## 3. Goals

## 4. Non-goals

## 5. Proposed Design

## 6. Data Model Changes

## 7. Backend Changes

## 8. Frontend Changes

## 9. API Changes

## 10. Tests

## 11. Verification Commands

## 12. Risks

## 13. Follow-up
`
}

function renderAdr(input: DocInput): string {
  return `${renderFrontmatter({ ...input, type: 'adr', phase: input.phase || 'global' })}# ${input.title}

## Status

${input.status || 'draft'}

## Context

## Decision

## Consequences

### Positive

### Negative

## Alternatives Considered

## Related Documents
`
}

function renderPhase(input: DocInput): string {
  return `${renderFrontmatter(input)}# ${input.title}

## Goal

## Deliverables

## Acceptance Criteria

## In Scope

## Out of Scope

## Task Breakdown

## Progress

## Risks

## Verification
`
}

function renderReview(input: DocInput): string {
  return `${renderFrontmatter(input)}# ${input.title}

## Review Target

## Summary

## Findings

### P0 - Must Fix

### P1 - Should Fix

### P2 - Nice to Have

## Architecture Boundary Check

- [ ] Incident remains central
- [ ] External systems are behind adapters
- [ ] AI does not directly execute production actions
- [ ] Risky actions require approval
- [ ] Tenant isolation is preserved
- [ ] Audit logs are created for sensitive operations

## Test Results

## Final Verdict
`
}

function renderFix(input: DocInput): string {
  return `${renderFrontmatter(input)}# ${input.title}

## Problem

## Impact

## Root Cause

## Fix Plan

## Files to Change

## Implementation Details

## Tests

## Verification Commands

## Rollback Plan

## Follow-up
`
}

function renderRunbook(input: DocInput): string {
  return `${renderFrontmatter(input)}# ${input.title}

## Purpose

## Risk Level

low | medium | high

## Approval Required

yes | no

## Inputs

## Preconditions

## Steps

### Step 1

## Expected Output

## Health Check

## Rollback

## Audit Requirements
`
}

function renderResearch(input: DocInput): string {
  return `${renderFrontmatter(input)}# ${input.title}

## Research Question

## Summary

## Findings

## Product Implications

## Technical Implications

## Recommended Decision

## Open Questions

## References
`
}

function starterDoc(opts: {
  title: string
  type: string
  status: string
  phase: string
  body: string
}): string {
  return `${renderFrontmatter({ title: opts.title, type: opts.type, status: opts.status, phase: opts.phase })}${opts.body}`
}

function docsReadme(): string {
  return `---
title: Documentation Guide
type: operation
status: accepted
phase: global
owner: ai
created: ${today()}
updated: ${today()}
related:
  - docs/INDEX.md
---

# Documentation Guide

This directory contains all project documentation for AegisOps / FaultLens.

## Main Sections

- architecture: stable architecture documents
- adr: architecture decision records
- phases: phase plans, acceptance, and progress
- designs: feature designs by phase
- reviews: design and code reviews
- fixes: bugfix and repair plans
- api: API specifications and conventions
- database: schema and migration docs
- integrations: external system integration docs
- ai: AI, RCA, prompt, and tool-calling docs
- operations: local development, deployment, troubleshooting
- runbooks: built-in product runbooks
- research: product and technical research
- _templates: document templates

## Required Frontmatter

Every document must have YAML frontmatter:

\`\`\`yaml
---
title: Document Title
type: design
status: draft
phase: phase-0
owner: ai
created: ${today()}
updated: ${today()}
related: []
---
\`\`\`

## Commands

\`\`\`bash
npx tsx scripts/docs.ts init
npx tsx scripts/docs.ts new design project-foundation --title "Project Foundation Design" --phase phase-0
npx tsx scripts/docs.ts new adr use-java-spring-boot --title "Use Java Spring Boot"
npx tsx scripts/docs.ts new fix zabbix-auth-failed --title "Fix Zabbix Auth Failed" --phase phase-1
npx tsx scripts/docs.ts check
npx tsx scripts/docs.ts index
\`\`\`

## Allowed Document Types

- architecture
- adr
- phase
- design
- review
- fix
- api
- database
- integration
- ai
- operation
- runbook
- research

## Allowed Status Values

- draft
- review
- accepted
- deprecated

## Naming Conventions

- Use lowercase filenames with kebab-case
- Use date prefix for process documents: \`YYYY-MM-DD-slug.md\`
- Use numbered filenames for ADR: \`NNNN-slug.md\`
- Do not use spaces in filenames

## Important Rules

- Do not create documents outside \`docs/\`
- Do not edit \`docs/INDEX.md\` manually (it is generated)
- Accepted ADRs should not be rewritten — create a new ADR if the decision changes
- All phase-scoped documents (designs, reviews, fixes) must be placed under the correct phase directory
- Stable documents (architecture, api, database, integrations, ai, operations, runbooks, research) go at the top level
`
}

function templateDesign(): string {
  return renderDesign({
    title: 'Design Template',
    type: 'design',
    status: 'draft',
    phase: 'phase-x',
  })
}

function templateAdr(): string {
  return renderAdr({
    title: 'ADR Template',
    type: 'adr',
    status: 'draft',
    phase: 'global',
  })
}

function templatePhase(): string {
  return renderPhase({
    title: 'Phase Template',
    type: 'phase',
    status: 'draft',
    phase: 'phase-x',
  })
}

function templateReview(): string {
  return renderReview({
    title: 'Review Template',
    type: 'review',
    status: 'draft',
    phase: 'phase-x',
  })
}

function templateFix(): string {
  return renderFix({
    title: 'Fix Template',
    type: 'fix',
    status: 'draft',
    phase: 'phase-x',
  })
}

function templateRunbook(): string {
  return renderRunbook({
    title: 'Runbook Template',
    type: 'runbook',
    status: 'draft',
    phase: 'global',
  })
}

function templateResearch(): string {
  return renderResearch({
    title: 'Research Template',
    type: 'research',
    status: 'draft',
    phase: 'global',
  })
}

function parseFlags(args: string[]): Record<string, string | boolean> {
  const flags: Record<string, string | boolean> = {}

  for (let i = 0; i < args.length; i++) {
    const arg = args[i]
    if (!arg.startsWith('--')) continue

    const key = arg.slice(2)
    const value = args[i + 1]

    if (!value || value.startsWith('--')) {
      flags[key] = true
      continue
    }

    flags[key] = value
    i++
  }

  return flags
}

interface Frontmatter {
  title?: string
  type?: string
  status?: string
  phase?: string
  created?: string
  updated?: string
  owner?: string
  related?: string[]
  [key: string]: string | string[] | undefined
}

function parseFrontmatter(content: string): Frontmatter | null {
  const normalized = content.replace(/\r\n/g, '\n')
  const match = normalized.match(/^---\n([\s\S]*?)\n---/)
  if (!match) return null

  const raw = match[1]
  const result: Frontmatter = {}
  const lines = raw.split('\n')

  let currentArrayKey: string | null = null

  for (const line of lines) {
    if (/^\s*-\s+/.test(line) && currentArrayKey) {
      ;(result[currentArrayKey] as string[]).push(
        line.replace(/^\s*-\s+/, '').trim()
      )
      continue
    }

    const pair = line.match(/^([a-zA-Z0-9_-]+):\s*(.*)$/)
    if (!pair) continue

    const [, key, value] = pair

    if (value === '[]' || value === '') {
      result[key] = []
      currentArrayKey = key
      continue
    }

    result[key] = value.trim()
    currentArrayKey = null
  }

  return result
}

function listMarkdownFiles(dir: string): string[] {
  if (!fs.existsSync(dir)) return []

  const result: string[] = []
  const entries = fs.readdirSync(dir, { withFileTypes: true })

  for (const entry of entries) {
    const full = path.join(dir, entry.name)

    if (entry.isDirectory()) {
      if (['node_modules', '.git', 'dist', 'build'].includes(entry.name))
        continue
      result.push(...listMarkdownFiles(full))
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      result.push(full)
    }
  }

  return result
}

function nextAdrNumber(): string {
  const adrDir = path.join(docsRoot, 'adr')
  ensureDir(adrDir)

  const nums = fs
    .readdirSync(adrDir)
    .map((name) => name.match(/^(\d{4})-/)?.[1])
    .filter((n): n is string => Boolean(n))
    .map((v) => Number(v))

  const next = nums.length > 0 ? Math.max(...nums) + 1 : 1
  return String(next).padStart(4, '0')
}

function defaultPhaseForType(type: string): string {
  if (
    [
      'architecture',
      'adr',
      'api',
      'database',
      'integration',
      'ai',
      'operation',
      'runbook',
      'research',
    ].includes(type)
  ) {
    return 'global'
  }
  return 'phase-0'
}

function normalizeSlug(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function toTitle(slug: string): string {
  return slug
    .replace(/[-_]+/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase())
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

function ensureDir(dir: string): void {
  fs.mkdirSync(dir, { recursive: true })
}

function writeFileIfAbsent(file: string, content: string): void {
  if (fs.existsSync(file)) return
  ensureDir(path.dirname(file))
  fs.writeFileSync(file, content, 'utf8')
}

function relative(file: string): string {
  return path.relative(root, file).replaceAll(path.sep, '/')
}

function toMarkdownLink(rel: string): string {
  return rel.startsWith('docs/') ? rel.slice('docs/'.length) : rel
}

function escapeTable(value: string): string {
  return String(value).replaceAll('|', '\\|')
}

function error(msg: string): void {
  console.error(`ERROR: ${msg}`)
}

function warn(msg: string): void {
  console.warn(`WARN: ${msg}`)
}

function fail(msg: string): never {
  console.error(msg)
  process.exit(1)
}

main()

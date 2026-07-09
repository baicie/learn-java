#!/usr/bin/env node

/**
 * Docs CI - validates docs structure and Phase 0 contracts
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { printSection, repoRoot } from './lib.js'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function exists(path: string): boolean {
  return existsSync(resolve(repoRoot, path))
}

function read(path: string): string {
  return readFileSync(resolve(repoRoot, path), 'utf8')
}

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message)
}

function assertFile(path: string): string {
  assert(exists(path), `Required file is missing: ${path}`)
  return read(path)
}

function assertIncludes(content: string, expected: string, file: string) {
  assert(
    content.includes(expected),
    `${file} must include required text: ${expected}`
  )
}

// ---------------------------------------------------------------------------
// Phase 0 Contract Checks
// ---------------------------------------------------------------------------

function checkPhase0Contracts() {
  printSection('Work Record Phase 0 Contracts')

  // 1. Check required docs exist
  const requiredDocs = [
    'docs/record/enterprise-roadmap.md',
    'docs/record/api-contract.md',
    'docs/record/schema-contract.md',
    'docs/record/permission-contract.md',
    'docs/record/acceptance-checklist.md',
  ]
  for (const file of requiredDocs) {
    assertFile(file)
  }
  console.log('  ✓ All required contract docs exist')

  // 2. Check enterprise-roadmap.md
  const roadmapFile = 'docs/record/enterprise-roadmap.md'
  const roadmapContent = assertFile(roadmapFile)
  assertIncludes(roadmapContent, 'feat/record-doc-portal', roadmapFile)
  assertIncludes(roadmapContent, 'web/portal', roadmapFile)
  assertIncludes(roadmapContent, 'web/console', roadmapFile)
  assertIncludes(roadmapContent, '不新增微服务', roadmapFile)
  assertIncludes(roadmapContent, '不新增业务 API', roadmapFile)
  assertIncludes(roadmapContent, '不新增数据库 migration', roadmapFile)
  console.log('  ✓ enterprise-roadmap.md contains required boundaries')

  // 3. Check api-contract.md
  const apiFile = 'docs/record/api-contract.md'
  const apiContent = assertFile(apiFile)
  for (const endpoint of [
    'GET    /api/platform/dictionaries',
    'GET    /api/platform/calendar-days/check',
    'GET    /api/work-record/templates',
    'POST   /api/work-record/templates/{templateId}/publish',
    'GET    /api/work-record/records',
    'POST   /api/work-record/records/export',
  ]) {
    assertIncludes(apiContent, endpoint, apiFile)
  }
  assertIncludes(apiContent, '统一响应结构', apiFile)
  assertIncludes(apiContent, '分页响应', apiFile)
  console.log('  ✓ api-contract.md contains required endpoints')

  // 4. Check schema-contract.md
  const schemaFile = 'docs/record/schema-contract.md'
  const schemaContent = assertFile(schemaFile)
  assertIncludes(schemaContent, 'x-work-record', schemaFile)
  assertIncludes(schemaContent, '^[a-zA-Z][a-zA-Z0-9_]', schemaFile)
  assertIncludes(schemaContent, 'template_version_id', schemaFile)
  assertIncludes(schemaContent, 'ISO_OFFSET_DATE_TIME', schemaFile)
  assertIncludes(schemaContent, 'dict', schemaFile)
  assertIncludes(schemaContent, 'fieldCode', schemaFile)
  console.log('  ✓ schema-contract.md contains required field rules')

  // 5. Check permission-contract.md
  const permFile = 'docs/record/permission-contract.md'
  const permContent = assertFile(permFile)
  for (const permission of [
    'platform:dict:read',
    'platform:dict:write',
    'platform:calendar:read',
    'platform:calendar:write',
    'platform:calendar:import',
    'work-record:template:read',
    'work-record:template:write',
    'work-record:read:self',
    'work-record:read:all',
    'work-record:write',
    'work-record:delete',
    'work-record:export',
  ]) {
    assertIncludes(permContent, permission, permFile)
  }
  assertIncludes(permContent, 'creator_id', permFile)
  assertIncludes(permContent, 'owner_id', permFile)
  console.log('  ✓ permission-contract.md contains required permissions')

  // 6. Check acceptance-checklist.md
  const checklistFile = 'docs/record/acceptance-checklist.md'
  const checklistContent = assertFile(checklistFile)
  for (const item of [
    '冻结',
    'web/portal',
    'web/console',
    'Phase 0 验收',
    'schema_json',
    'wr_template_field',
    '普通用户',
    '导出',
    '403',
  ]) {
    assertIncludes(checklistContent, item, checklistFile)
  }
  console.log('  ✓ acceptance-checklist.md contains required items')

  // 7. Check package.json scripts reference web/portal
  const pkgFile = 'package.json'
  const pkg = JSON.parse(assertFile(pkgFile))
  for (const scriptName of ['build', 'lint', 'typecheck', 'test']) {
    const script = pkg.scripts?.[scriptName]
    assert(script, `package.json scripts.${scriptName} is required`)
    assert(
      script.includes('web/portal'),
      `package.json scripts.${scriptName} must reference web/portal`
    )
  }
  console.log('  ✓ package.json scripts reference web/portal')

  // 8. Check frontend.sh makes console opt-in
  const frontendSh = 'scripts/ci/frontend.sh'
  const frontendShContent = assertFile(frontendSh)
  assertIncludes(frontendShContent, 'run_frontend "$PORTAL_DIR"', frontendSh)
  assertIncludes(frontendShContent, 'RUN_LEGACY_CONSOLE_CI', frontendSh)
  assertIncludes(frontendShContent, 'RUN_LEGACY_CONSOLE_CI=1', frontendSh)
  console.log('  ✓ frontend.sh makes console opt-in')

  console.log('  ✅ Work Record Phase 0 contracts check passed')
}

// ---------------------------------------------------------------------------
// Docs Structure Check
// ---------------------------------------------------------------------------

function checkDocsStructure() {
  printSection('Docs Structure')

  if (!exists('docs/INDEX.md')) {
    console.log('Skip docs: docs/INDEX.md not found.')
    return
  }

  console.log('Docs directory exists, checking structure...')

  for (const [label, dir] of [
    ['docs/_templates', 'docs/_templates'],
    ['docs/architecture', 'docs/architecture'],
    ['docs/designs', 'docs/designs'],
    ['docs/phases', 'docs/phases'],
  ] as const) {
    if (!exists(dir)) continue
    const count = readdirSync(resolve(repoRoot, dir)).length
    console.log(
      `  - ${label}: ${count} ${label === 'docs/_templates' ? 'templates' : 'files'}`
    )
  }

  console.log('  ✅ Docs structure check passed')
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  checkDocsStructure()
  checkPhase0Contracts()

  // Run scripts/docs.ts check
  printSection('Docs Frontmatter Check')
  const { spawn } = await import('node:child_process')
  await new Promise<void>((resolve, reject) => {
    const child = spawn('pnpm', ['exec', 'tsx', 'scripts/docs.ts', 'check'], {
      cwd: repoRoot,
      stdio: 'inherit',
    })
    child.on('error', reject)
    child.on('exit', (code) => {
      if (code === 0) {
        resolve()
      } else {
        reject(new Error(`docs.ts check failed with code ${code}`))
      }
    })
  })

  console.log('\n✅ All docs CI checks passed.')
}

main().catch((err) => {
  console.error('\n❌ Docs CI failed:', err.message)
  process.exit(1)
})

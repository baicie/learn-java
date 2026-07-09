#!/usr/bin/env node

/**
 * Phase 0 Contract Checker for Work Record
 *
 * Validates that the Phase 0 contracts are properly implemented:
 * - docs/record/enterprise-roadmap.md
 * - docs/record/api-contract.md
 * - docs/record/schema-contract.md
 * - docs/record/permission-contract.md
 * - docs/record/acceptance-checklist.md
 * - package.json scripts reference web/portal
 * - scripts/ci/frontend.sh makes console opt-in
 */

import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const repoRoot = resolve(__dirname, '../..')

function read(path: string): string {
  return readFileSync(resolve(repoRoot, path), 'utf8')
}

function exists(path: string): boolean {
  return existsSync(resolve(repoRoot, path))
}

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) {
    throw new Error(message)
  }
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

function assertNotIncludes(content: string, forbidden: string, file: string) {
  assert(
    !content.includes(forbidden),
    `${file} must not include forbidden text: ${forbidden}`
  )
}

function checkDocsExist() {
  for (const file of [
    'docs/record/enterprise-roadmap.md',
    'docs/record/api-contract.md',
    'docs/record/schema-contract.md',
    'docs/record/permission-contract.md',
    'docs/record/acceptance-checklist.md',
  ]) {
    assertFile(file)
  }
  console.log('✓ All required contract docs exist')
}

function checkRoadmap() {
  const file = 'docs/record/enterprise-roadmap.md'
  const content = assertFile(file)

  assertIncludes(content, 'feat/record-doc-portal', file)
  assertIncludes(content, 'web/portal', file)
  assertIncludes(content, 'web/console 不再新增工作记录相关功能', file)
  assertIncludes(content, 'RUN_LEGACY_CONSOLE_CI=1', file)
  assertIncludes(content, '不新增微服务', file)
  assertIncludes(content, '不新增业务 API', file)
  assertIncludes(content, '不新增数据库 migration', file)
  console.log('✓ enterprise-roadmap.md contains required boundaries')
}

function checkApiContract() {
  const file = 'docs/record/api-contract.md'
  const content = assertFile(file)

  for (const endpoint of [
    'GET    /api/platform/dictionaries',
    'GET    /api/platform/calendar-days/check',
    'GET    /api/work-record/templates',
    'POST   /api/work-record/templates/{templateId}/publish',
    'GET    /api/work-record/records',
    'POST   /api/work-record/records/export',
  ]) {
    assertIncludes(content, endpoint, file)
  }

  assertIncludes(content, 'JSONB 查询必须参数化', file)
  assertIncludes(content, '导出必须写审计', file)
  console.log('✓ api-contract.md contains required endpoints')
}

function checkSchemaContract() {
  const file = 'docs/record/schema-contract.md'
  const content = assertFile(file)

  assertIncludes(content, 'x-work-record', file)
  assertIncludes(content, '^[a-zA-Z][a-zA-Z0-9_]{0,63}$', file)
  assertIncludes(content, 'template_version_id', file)
  assertIncludes(content, 'ISO_OFFSET_DATE_TIME', file)
  assertIncludes(content, 'dict select 值必须在启用字典项里', file)
  assertIncludes(content, 'fieldCode 创建后不可随意修改', file)

  assertNotIncludes(content, '新的扁平扩展字段作为主协议', file)
  console.log('✓ schema-contract.md contains required field rules')
}

function checkPermissionContract() {
  const file = 'docs/record/permission-contract.md'
  const content = assertFile(file)

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
    assertIncludes(content, permission, file)
  }

  assertIncludes(content, 'creator_id', file)
  assertIncludes(content, 'owner_id', file)
  console.log('✓ permission-contract.md contains required permissions')
}

function checkAcceptanceChecklist() {
  const file = 'docs/record/acceptance-checklist.md'
  const content = assertFile(file)

  for (const item of [
    '冻结 `feat/record-doc-portal` 当前不可用实现',
    '明确后续只在 `web/portal` 新增工作记录功能',
    '明确 `web/console` 不再新增工作记录功能',
    '`scripts/ci/check-record-phase0-contracts.ts` 已接入 docs CI',
    '`web/console` 只在 `RUN_LEGACY_CONSOLE_CI=1` 时运行',
    'schema_json 与 wr_template_field 同步一致',
    '普通用户不可查看别人记录',
    '动态字段能导出真实值',
    '无权限返回 403',
  ]) {
    assertIncludes(content, item, file)
  }
  console.log('✓ acceptance-checklist.md contains required items')
}

function checkRootPackageUsesPortal() {
  const file = 'package.json'
  const pkg = JSON.parse(assertFile(file)) as {
    scripts?: Record<string, string>
  }

  for (const scriptName of ['build', 'lint', 'typecheck', 'test']) {
    const script = pkg.scripts?.[scriptName]
    assert(script, `package.json scripts.${scriptName} is required`)
    assert(
      script.includes('web/portal'),
      `package.json scripts.${scriptName} must reference web/portal`
    )
  }
  console.log('✓ package.json scripts reference web/portal')
}

function checkFrontendCiConsoleIsOptIn() {
  const file = 'scripts/ci/frontend.sh'
  const content = assertFile(file)

  assertIncludes(content, 'ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"', file)
  assertIncludes(content, 'run_frontend "$PORTAL_DIR"', file)
  assertIncludes(content, 'RUN_LEGACY_CONSOLE_CI', file)
  assertIncludes(content, 'Legacy console CI is disabled by default', file)
  console.log('✓ frontend.sh makes console opt-in')
}

function main() {
  console.log('Checking Work Record Phase 0 contracts...\n')

  checkDocsExist()
  checkRoadmap()
  checkApiContract()
  checkSchemaContract()
  checkPermissionContract()
  checkAcceptanceChecklist()
  checkRootPackageUsesPortal()
  checkFrontendCiConsoleIsOptIn()

  console.log('\n✅ All Phase 0 contract checks passed.')
}

main()

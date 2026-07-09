#!/usr/bin/env node

/**
 * Phase 1 Engineering Baseline Contract Checker
 *
 * Validates that Phase 1 engineering baseline is properly established:
 * - root package.json scripts reference web/portal
 * - aiops-server has both with-portal and with-console profiles
 * - frontend CI makes console opt-in
 * - backend CI explicitly tests platform/work-record/server modules
 * - record feature pages are frozen
 * - backend boundary tests exist
 */

import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const repoRoot = resolve(__dirname, '../..')

function file(path: string): string {
  const abs = resolve(repoRoot, path)
  if (!existsSync(abs)) {
    throw new Error(`Required file is missing: ${path}`)
  }
  return readFileSync(abs, 'utf8')
}

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) {
    throw new Error(message)
  }
}

function includes(path: string, expected: string) {
  const content = file(path)
  assert(
    content.includes(expected),
    `${path} must include required text: ${expected}`
  )
}

function checkRootPackageScripts() {
  const pkg = JSON.parse(file('package.json')) as {
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

function checkServerProfiles() {
  const pom = file('apps/aiops-server/pom.xml')

  for (const required of [
    '<id>with-portal</id>',
    '<id>with-console</id>',
    'web/portal/dist',
    'web/console/dist',
    '<id>portal-build</id>',
    '<id>console-build</id>',
  ]) {
    assert(
      pom.includes(required),
      `apps/aiops-server/pom.xml must include ${required}`
    )
  }
  console.log('✓ aiops-server has both with-portal and with-console profiles')
}

function checkFrontendCi() {
  const script = file('scripts/ci/frontend.sh')

  for (const required of [
    'ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"',
    'run_frontend "$PORTAL_DIR"',
    'RUN_LEGACY_CONSOLE_CI',
    'Legacy console CI is disabled by default',
  ]) {
    assert(script.includes(required), `frontend.sh must include ${required}`)
  }
  console.log('✓ frontend.sh makes console opt-in')
}

function checkBackendCi() {
  const script = file('scripts/ci/backend.sh')

  for (const required of [
    '-pl modules/aiops-platform -am test',
    '-pl modules/aiops-work-record -am test',
    '-pl apps/aiops-server -am test',
  ]) {
    assert(script.includes(required), `backend.sh must include ${required}`)
  }
  console.log(
    '✓ backend.sh explicitly tests platform/work-record/server modules'
  )
}

function checkRecordFeatureFrozen() {
  for (const path of [
    'web/portal/src/features/work-records/components/work-record-feature-frozen.tsx',
    'web/portal/src/features/work-records/index.tsx',
    'web/portal/src/features/work-records/new.tsx',
    'web/portal/src/features/work-records/edit.tsx',
    'web/portal/src/features/work-records/detail.tsx',
    'web/portal/src/features/work-records/designer.tsx',
  ]) {
    file(path)
  }

  includes(
    'web/portal/src/features/work-records/components/work-record-feature-frozen.tsx',
    '工作记录模块重做中'
  )
  console.log('✓ record feature pages are frozen')
}

function checkBackendBoundaryTests() {
  for (const path of [
    'modules/aiops-work-record/src/test/java/io/aegisops/workrecord/architecture/WorkRecordModuleBoundaryTest.java',
    'modules/aiops-platform/src/test/java/io/aegisops/platform/architecture/PlatformModuleBoundaryTest.java',
    'apps/aiops-server/src/test/java/io/aegisops/server/PortalPackagingProfileContractTest.java',
  ]) {
    file(path)
  }
  console.log('✓ backend boundary tests exist')
}

function checkPhase1Doc() {
  file('docs/record/phase-01-engineering-baseline.md')
  console.log('✓ Phase 1 documentation exists')
}

function main() {
  console.log('Checking Work Record Phase 1 engineering baseline...\n')

  checkRootPackageScripts()
  checkServerProfiles()
  checkFrontendCi()
  checkBackendCi()
  checkRecordFeatureFrozen()
  checkBackendBoundaryTests()
  checkPhase1Doc()

  console.log('\n✅ All Phase 1 baseline checks passed.')
}

main()

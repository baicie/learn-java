import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const repoRoot = fileURLToPath(new URL('../../', import.meta.url))

test('Formily dependency guard reads repository paths without path errors', () => {
  const result = spawnSync('bash', ['scripts/ci/check-formily-deps.sh'], {
    cwd: repoRoot,
    encoding: 'utf8',
  })

  assertGuardSucceeded(result)
})

test('Portal features guard reads repository paths without path errors', () => {
  const result = spawnSync(
    process.execPath,
    ['scripts/ci/check-portal-no-features.mjs'],
    {
      cwd: repoRoot,
      encoding: 'utf8',
    },
  )

  assertGuardSucceeded(result)
})

function assertGuardSucceeded(result) {
  const output = `${result.stdout ?? ''}${result.stderr ?? ''}`

  assert.equal(result.error, undefined)
  assert.equal(result.status, 0, output)
  assert.doesNotMatch(output, /ENOENT/)
}

import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { test } from 'node:test'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..')

async function waitForFrontend(
  child: ReturnType<typeof spawn>,
  timeoutMs = 15_000
): Promise<void> {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    assert.equal(
      child.exitCode,
      null,
      `frontend command exited before Vite was ready (code=${child.exitCode})`
    )
    try {
      const response = await fetch('http://127.0.0.1:5173')
      if (response.ok) return
    } catch {
      // Vite has not bound the port yet.
    }
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  assert.fail('frontend did not become ready within 15 seconds')
}

test('frontend command remains alive after Vite becomes ready', async (context) => {
  const child = spawn(
    process.execPath,
    ['--import', 'tsx', 'scripts/start.ts', 'frontend'],
    {
      cwd: repoRoot,
      detached: process.platform !== 'win32',
      env: { ...process.env, NO_PROXY: '127.0.0.1,localhost' },
      stdio: 'ignore',
    }
  )

  context.after(() => {
    if (!child.pid) return
    try {
      if (process.platform === 'win32') child.kill()
      else process.kill(-child.pid, 'SIGTERM')
    } catch {
      // The process group may already be gone.
    }
  })

  await waitForFrontend(child)
  await new Promise((resolve) => setTimeout(resolve, 500))
  assert.equal(child.exitCode, null)
})

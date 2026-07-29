import assert from 'node:assert/strict'
import { spawn, spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..')

test('dev command starts the worker required by queued datasource syncs', () => {
  const source = readFileSync(join(repoRoot, 'scripts/start.ts'), 'utf8')

  assert.match(
    source,
    /startSelectedBackends\(\[["']server["'], ["']worker["']\]\)/
  )
  assert.match(source, /-am clean package/)
  assert.match(source, /Worker:\s+http:\/\/localhost:8091/)
})

test('local backend startup does not inject an AI model key', () => {
  const source = readFileSync(join(repoRoot, 'scripts/start.ts'), 'utf8')

  assert.doesNotMatch(source, /AIOPS_AI_MODEL_SECRET/)
})

test('local runner uses the registered 8092 port from app metadata', () => {
  const source = readFileSync(join(repoRoot, 'scripts/start.ts'), 'utf8')
  const readme = readFileSync(join(repoRoot, 'scripts/README-start.md'), 'utf8')
  const envExample = readFileSync(join(repoRoot, 'infra/env.example'), 'utf8')

  assert.match(
    source,
    /runner:\s*{[^}]*port:\s*8092[^}]*portEnv:\s*['"]AIOPS_RUNNER_PORT['"]/s
  )
  assert.match(source, /env\[app\.portEnv\]\s*=\s*String\(app\.port\)/)
  assert.doesNotMatch(source, /8082/)
  assert.match(readme, /runner[^\n]*8092/i)
  assert.doesNotMatch(readme, /8082/)
  assert.match(envExample, /^AIOPS_RUNNER_PORT=8092$/m)
})

function composeServiceBlock(source: string, serviceName: string): string {
  const pattern = new RegExp(
    `^  ${serviceName}:\\r?\\n([\\s\\S]*?)(?=^  [a-zA-Z0-9-]+:|^volumes:)`,
    'm'
  )
  const match = source.match(pattern)
  assert.ok(match, `missing compose service: ${serviceName}`)
  return match[0]
}

test('local compose restarts every long-lived dependency', () => {
  const compose = readFileSync(
    join(repoRoot, 'infra/docker-compose.yml'),
    'utf8'
  )

  for (const service of [
    'postgres',
    'redis',
    'victoriametrics',
    'minio',
    'zabbix-postgres',
  ]) {
    assert.match(
      composeServiceBlock(compose, service),
      /^    restart: unless-stopped\s*$/m,
      `${service} must survive Docker Desktop restarts`
    )
  }

  assert.match(
    composeServiceBlock(compose, 'minio-init'),
    /^    restart: ['"]no['"]\s*$/m
  )
})

test('Zabbix demo injects trapper history without the removed demo app', () => {
  const setup = readFileSync(
    join(repoRoot, 'scripts/demo/setup-zabbix-demo.py'),
    'utf8'
  )
  const inject = readFileSync(
    join(repoRoot, 'scripts/demo/inject-zabbix-incident.sh'),
    'utf8'
  )

  assert.match(setup, /^ZABBIX_TRAPPER_TYPE\s*=\s*2$/m)
  assert.match(setup, /["']history\.push["']/)
  assert.doesNotMatch(setup, /demo_base_url|HTTP_AGENT_TYPE/)
  assert.match(inject, /setup-zabbix-demo\.py/)
  assert.doesNotMatch(
    inject,
    /8088|demo-order-service|AIOPS_DEMO_ORDER_BASE_URL/
  )
})

test('Zabbix webhook contract demos obtain a datasource-scoped token', () => {
  const nodeDemo = readFileSync(
    join(repoRoot, 'scripts/demo-zabbix-scenario.mjs'),
    'utf8'
  )
  const bashDemo = readFileSync(
    join(repoRoot, 'scripts/demo-zabbix-scenario.sh'),
    'utf8'
  )

  for (const source of [nodeDemo, bashDemo]) {
    assert.match(source, /\/api\/datasources\/.*zabbix-webhook-token/)
    assert.match(source, /\/api\/datasources\/.*\/test/)
    assert.doesNotMatch(source, /AIOPS_ZABBIX_WEBHOOK_TOKEN/)
    assert.doesNotMatch(source, /dev-zabbix-webhook-token/)
  }
})

async function waitForFrontend(
  child: ReturnType<typeof spawn>,
  timeoutMs = 60_000
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
  assert.fail(`frontend did not become ready within ${timeoutMs}ms`)
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
      if (process.platform === 'win32') {
        spawnSync('taskkill', ['/pid', String(child.pid), '/t', '/f'], {
          stdio: 'ignore',
        })
      } else process.kill(-child.pid, 'SIGTERM')
    } catch {
      // The process group may already be gone.
    }
  })

  await waitForFrontend(child)
  await new Promise((resolve) => setTimeout(resolve, 500))
  assert.equal(child.exitCode, null)
})

import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createServer, type IncomingMessage } from 'node:http'
import { once } from 'node:events'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const legacyEventIds = ['20001', '20002', '20003', '20004']
const expectedTriggerIds = ['30001', '30002', '30003', '30004']

type DemoWebhookPayload = {
  eventId: string
  problemId: string
  triggerId: string
  objectId: string
  startsAt: string
}

test('demo scenario exits non-zero when aggregation produces no incident', async () => {
  const server = createServer((request, response) => {
    const body = responseFor(request.method, request.url ?? '')
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify(body))
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')

  const address = server.address()
  assert.ok(address && typeof address !== 'string')
  const baseUrl = `http://127.0.0.1:${address.port}`

  try {
    const result = await runScenario(baseUrl)
    assert.equal(result.exitCode, 1, result.stderr)
    assert.match(result.stderr, /No incident found after aggregation/)
  } finally {
    server.close()
    await once(server, 'close')
  }
})

test('demo scenario does not report success with an unrelated historical incident', async () => {
  const server = createServer((request, response) => {
    const url = request.url ?? ''
    if (request.method === 'POST' && url === '/api/incidents/aggregate') {
      response.writeHead(500, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ message: 'aggregation failed' }))
      return
    }
    if (request.method === 'GET' && url === '/api/incidents') {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ data: [{ id: 'inc-historical' }] }))
      return
    }
    if (url.includes('/api/incidents/inc-historical/')) {
      response.writeHead(500, { 'Content-Type': 'application/json' })
      response.end(
        JSON.stringify({ message: 'historical incident unavailable' })
      )
      return
    }

    const body = responseFor(request.method, url)
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify(body))
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')

  const address = server.address()
  assert.ok(address && typeof address !== 'string')
  const baseUrl = `http://127.0.0.1:${address.port}`

  try {
    const result = await runScenario(baseUrl)
    assert.equal(result.exitCode, 1, result.stdout)
    assert.match(result.stderr, /Incident aggregation failed/)
    assert.doesNotMatch(result.stdout, /Z9 Demo Complete/)
  } finally {
    server.close()
    await once(server, 'close')
  }
})

test('demo scenario rejects an unrelated incident after successful aggregation', async () => {
  const server = createServer((request, response) => {
    const url = request.url ?? ''
    if (request.method === 'POST' && url === '/api/incidents/aggregate') {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ data: { alertsLinked: 0 } }))
      return
    }
    if (request.method === 'GET' && url === '/api/incidents') {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ data: [{ id: 'inc-historical' }] }))
      return
    }
    if (
      request.method === 'GET' &&
      url === '/api/incidents/inc-historical/alerts'
    ) {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(
        JSON.stringify({
          data: legacyEventIds.map((eventId) => ({
            sourceEventId: `ds-demo:${eventId}`,
          })),
        })
      )
      return
    }
    if (url.includes('/api/incidents/inc-historical/')) {
      response.writeHead(500, { 'Content-Type': 'application/json' })
      response.end(
        JSON.stringify({ message: 'historical incident unavailable' })
      )
      return
    }

    const body = responseFor(request.method, url)
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify(body))
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')

  const address = server.address()
  assert.ok(address && typeof address !== 'string')

  try {
    const result = await runScenario(`http://127.0.0.1:${address.port}`)
    assert.equal(result.exitCode, 1, result.stdout)
    assert.match(
      result.stderr,
      /No incident contains all injected Zabbix events/
    )
    assert.doesNotMatch(result.stdout, /Z9 Demo Complete/)
  } finally {
    server.close()
    await once(server, 'close')
  }
})

test('demo scenario generates fresh event IDs per run while preserving trigger IDs', async () => {
  const injectedEvents: DemoWebhookPayload[] = []
  const server = createServer(async (request, response) => {
    const url = request.url ?? ''
    if (
      request.method === 'POST' &&
      url.startsWith('/api/integrations/zabbix/events?')
    ) {
      injectedEvents.push(await readJsonBody(request))
    }

    const body = responseFor(request.method, url)
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify(body))
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')

  const address = server.address()
  assert.ok(address && typeof address !== 'string')
  const baseUrl = `http://127.0.0.1:${address.port}`

  try {
    const firstRun = await runScenario(baseUrl)
    const secondRun = await runScenario(baseUrl)
    assert.equal(firstRun.exitCode, 1, firstRun.stdout)
    assert.equal(secondRun.exitCode, 1, secondRun.stdout)

    const firstBatch = injectedEvents.slice(0, 4)
    const secondBatch = injectedEvents.slice(4, 8)
    assertDemoWebhookBatch(firstBatch)
    assertDemoWebhookBatch(secondBatch)
    assert.equal(
      new Set(injectedEvents.map((event) => event.eventId)).size,
      8,
      'separate runs must not reuse event IDs'
    )
  } finally {
    server.close()
    await once(server, 'close')
  }
})

test('demo scenario exits non-zero when a required downstream stage fails', async () => {
  const injectedEvents: DemoWebhookPayload[] = []
  const server = createServer(async (request, response) => {
    const url = request.url ?? ''
    if (
      request.method === 'POST' &&
      url.startsWith('/api/integrations/zabbix/events?')
    ) {
      injectedEvents.push(await readJsonBody(request))
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ data: { alertId: 'alert-demo' } }))
      return
    }
    if (request.method === 'POST' && url === '/api/incidents/aggregate') {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ data: { alertsLinked: 4 } }))
      return
    }
    if (request.method === 'GET' && url === '/api/incidents') {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ data: [{ id: 'inc-demo' }] }))
      return
    }
    if (request.method === 'GET' && url === '/api/incidents/inc-demo/alerts') {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(
        JSON.stringify({
          data: injectedEvents.map((event) => ({
            sourceEventId: `ds-demo:${event.eventId}`,
          })),
        })
      )
      return
    }
    if (
      request.method === 'POST' &&
      url === '/api/incidents/inc-demo/evidence/zabbix/collect'
    ) {
      response.writeHead(500, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ message: 'evidence unavailable' }))
      return
    }
    if (
      request.method === 'POST' &&
      url === '/api/incidents/inc-demo/rca/analyze'
    ) {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ data: {} }))
      return
    }
    if (
      request.method === 'POST' &&
      url === '/api/incidents/inc-demo/ai/diagnose'
    ) {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify({ data: {} }))
      return
    }
    if (
      request.method === 'POST' &&
      url === '/api/incidents/inc-demo/reports'
    ) {
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(
        JSON.stringify({
          data: { id: 'report-demo', title: 'Demo', versionNo: 1 },
        })
      )
      return
    }

    const body = responseFor(request.method, url)
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify(body))
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')

  const address = server.address()
  assert.ok(address && typeof address !== 'string')

  try {
    const result = await runScenario(`http://127.0.0.1:${address.port}`)
    assert.equal(result.exitCode, 1, result.stdout)
    assertDemoWebhookBatch(injectedEvents)
    assert.match(result.stderr, /Evidence collection failed/)
    assert.doesNotMatch(result.stdout, /Z9 Demo Complete/)
  } finally {
    server.close()
    await once(server, 'close')
  }
})

test('demo scenario rejects empty successful downstream payloads', async (t) => {
  const cases = [
    { stage: 'evidence', message: /Evidence collection returned no evidence/ },
    { stage: 'rca', message: /RCA analysis returned no root cause or rules/ },
    { stage: 'ai', message: /AI diagnosis returned incomplete content/ },
    {
      stage: 'report',
      message: /Report markdown is missing required sections/,
    },
  ] as const

  for (const scenario of cases) {
    await t.test(scenario.stage, async () => {
      const injectedEvents: DemoWebhookPayload[] = []
      const server = createServer(async (request, response) => {
        const url = request.url ?? ''
        if (
          request.method === 'POST' &&
          url.startsWith('/api/integrations/zabbix/events?')
        ) {
          injectedEvents.push(await readJsonBody(request))
        }

        const body = completedFlowResponse(
          request.method,
          url,
          injectedEvents,
          scenario.stage
        )
        response.writeHead(200, { 'Content-Type': 'application/json' })
        response.end(JSON.stringify(body))
      })
      server.listen(0, '127.0.0.1')
      await once(server, 'listening')

      const address = server.address()
      assert.ok(address && typeof address !== 'string')

      try {
        const result = await runScenario(`http://127.0.0.1:${address.port}`)
        assert.equal(result.exitCode, 1, result.stdout)
        assert.match(result.stderr, scenario.message)
        assert.doesNotMatch(result.stdout, /Z9 Demo Complete/)
      } finally {
        server.close()
        await once(server, 'close')
      }
    })
  }
})

test('demo scenario selects the Zabbix datasource matching its endpoint', async () => {
  const selectedDatasourceIds: string[] = []
  const server = createServer((request, response) => {
    const url = request.url ?? ''
    let body: object
    if (request.method === 'POST' && url === '/api/auth/login') {
      body = {
        data: { token: 'demo-token', user: { tenantId: 'tenant-demo' } },
      }
    } else if (request.method === 'GET' && url === '/api/datasources') {
      body = {
        data: [
          {
            id: 'ds-wrong',
            type: 'zabbix',
            name: 'Other Zabbix',
            endpoint: 'https://other.example/api_jsonrpc.php',
          },
          {
            id: 'ds-demo',
            type: 'zabbix',
            name: 'Demo Zabbix',
            endpoint: 'http://localhost:8081/api_jsonrpc.php',
          },
        ],
      }
    } else if (
      request.method === 'POST' &&
      /^\/api\/datasources\/[^/]+\/test$/.test(url)
    ) {
      selectedDatasourceIds.push(url.split('/')[3])
      body = { data: { ok: true } }
    } else if (
      request.method === 'GET' &&
      /^\/api\/datasources\/[^/]+\/zabbix-webhook-token$/.test(url)
    ) {
      selectedDatasourceIds.push(url.split('/')[3])
      body = { data: { token: 'zwh_demo-token' } }
    } else if (
      request.method === 'POST' &&
      url.startsWith('/api/integrations/zabbix/events?')
    ) {
      selectedDatasourceIds.push(
        new URL(url, 'http://localhost').searchParams.get('datasourceId') ?? ''
      )
      body = { data: { alertId: 'alert-demo' } }
    } else if (
      request.method === 'POST' &&
      url === '/api/incidents/aggregate'
    ) {
      body = { data: { alertsLinked: 0 } }
    } else if (request.method === 'GET' && url === '/api/incidents') {
      body = { data: [] }
    } else {
      throw new Error(
        `Unexpected demo scenario request: ${request.method} ${url}`
      )
    }
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify(body))
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')

  const address = server.address()
  assert.ok(address && typeof address !== 'string')

  try {
    const result = await runScenario(`http://127.0.0.1:${address.port}`)
    assert.equal(result.exitCode, 1, result.stdout)
    assert.ok(selectedDatasourceIds.length >= 6)
    assert.deepEqual(new Set(selectedDatasourceIds), new Set(['ds-demo']))
  } finally {
    server.close()
    await once(server, 'close')
  }
})

test('demo scenario aborts a request that exceeds its configured timeout', async () => {
  const server = createServer(() => {})
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')

  const address = server.address()
  assert.ok(address && typeof address !== 'string')

  try {
    const result = await runScenario(
      `http://127.0.0.1:${address.port}`,
      { AIOPS_HTTP_TIMEOUT_MS: '50' },
      1000
    )
    assert.equal(result.exitCode, 1, result.stderr)
    assert.match(result.stderr, /timed out/i)
  } finally {
    server.closeAllConnections()
    server.close()
    await once(server, 'close')
  }
})

function assertDemoWebhookBatch(events: DemoWebhookPayload[]): void {
  assert.equal(events.length, 4)
  assert.deepEqual(
    events.map((event) => event.triggerId),
    expectedTriggerIds
  )
  assert.equal(new Set(events.map((event) => event.eventId)).size, 4)
  assert.equal(
    new Set(events.map((event) => event.startsAt)).size,
    1,
    'all events in one run must use the same start time'
  )
  for (const event of events) {
    assert.match(event.eventId, /^\d+$/)
    assert.ok(!legacyEventIds.includes(event.eventId))
    assert.equal(event.problemId, event.eventId)
    assert.equal(event.objectId, event.triggerId)
  }
}

function completedFlowResponse(
  method: string | undefined,
  url: string,
  injectedEvents: DemoWebhookPayload[],
  emptyStage: 'evidence' | 'rca' | 'ai' | 'report'
): object {
  if (method === 'POST' && url === '/api/auth/login') {
    return { data: { token: 'demo-token', user: { tenantId: 'tenant-demo' } } }
  }
  if (method === 'GET' && url === '/api/datasources') {
    return {
      data: [
        {
          id: 'ds-demo',
          type: 'zabbix',
          name: 'Demo Zabbix',
          endpoint: 'http://localhost:8081/api_jsonrpc.php',
        },
      ],
    }
  }
  if (method === 'POST' && url === '/api/datasources/ds-demo/test') {
    return { data: { ok: true } }
  }
  if (
    method === 'GET' &&
    url === '/api/datasources/ds-demo/zabbix-webhook-token'
  ) {
    return { data: { token: 'zwh_demo-token' } }
  }
  if (method === 'POST' && url.startsWith('/api/integrations/zabbix/events?')) {
    return { data: { alertId: 'alert-demo' } }
  }
  if (method === 'POST' && url === '/api/incidents/aggregate') {
    return { data: { alertsLinked: 4 } }
  }
  if (method === 'GET' && url === '/api/incidents') {
    return { data: [{ id: 'inc-demo' }] }
  }
  if (method === 'GET' && url === '/api/incidents/inc-demo/alerts') {
    return {
      data: injectedEvents.map((event) => ({
        sourceEventId: `ds-demo:${event.eventId}`,
      })),
    }
  }
  if (
    method === 'POST' &&
    url === '/api/incidents/inc-demo/evidence/zabbix/collect'
  ) {
    return emptyStage === 'evidence'
      ? { data: {} }
      : { data: { evidenceCreated: 4, evidenceUpdated: 0 } }
  }
  if (method === 'POST' && url === '/api/incidents/inc-demo/rca/analyze') {
    return emptyStage === 'rca'
      ? { data: {} }
      : {
          data: {
            suspectedRootCause: 'Demo root cause',
            confidence: 0.8,
            matchedRules: ['R1_HIGH_SEVERITY'],
          },
        }
  }
  if (method === 'POST' && url === '/api/incidents/inc-demo/ai/diagnose') {
    return emptyStage === 'ai'
      ? { data: {} }
      : {
          data: {
            summary: 'Demo diagnosis',
            rootCause: 'Demo root cause',
            impact: 'Demo impact',
          },
        }
  }
  if (method === 'POST' && url === '/api/incidents/inc-demo/reports') {
    return {
      data: {
        id: 'report-demo',
        title: 'Demo',
        versionNo: 1,
        markdownContent:
          emptyStage === 'report'
            ? ''
            : '# \u6545\u969c\u62a5\u544a\n\n## \u4e94\u3001\u5173\u952e\u8bc1\u636e\n\n## \u4e03\u3001AI \u8bca\u65ad\n',
      },
    }
  }
  throw new Error(`Unexpected demo scenario request: ${method} ${url}`)
}

async function readJsonBody(
  request: IncomingMessage
): Promise<DemoWebhookPayload> {
  let body = ''
  for await (const chunk of request) {
    body += chunk
  }
  return JSON.parse(body) as DemoWebhookPayload
}

function responseFor(method: string | undefined, url: string): object {
  if (method === 'POST' && url === '/api/auth/login') {
    return { data: { token: 'demo-token', user: { tenantId: 'tenant-demo' } } }
  }
  if (method === 'GET' && url === '/api/datasources') {
    return {
      data: [
        {
          id: 'ds-demo',
          type: 'zabbix',
          name: 'Demo Zabbix',
          endpoint: 'http://localhost:8081/api_jsonrpc.php',
        },
      ],
    }
  }
  if (method === 'POST' && url === '/api/datasources/ds-demo/test') {
    return { data: { ok: true } }
  }
  if (
    method === 'GET' &&
    url === '/api/datasources/ds-demo/zabbix-webhook-token'
  ) {
    return { data: { token: 'zwh_demo-token' } }
  }
  if (method === 'POST' && url.startsWith('/api/integrations/zabbix/events?')) {
    return { data: { alertId: 'alert-demo' } }
  }
  if (method === 'POST' && url === '/api/incidents/aggregate') {
    return { data: { aggregated: 0 } }
  }
  if (method === 'GET' && url === '/api/incidents') {
    return { data: [] }
  }
  throw new Error(`Unexpected demo scenario request: ${method} ${url}`)
}

function runScenario(
  baseUrl: string,
  extraEnv: NodeJS.ProcessEnv = {},
  watchdogMs = 10_000
): Promise<{ exitCode: number | null; stdout: string; stderr: string }> {
  return new Promise((resolve, reject) => {
    const child = spawn(
      process.execPath,
      ['scripts/demo-zabbix-scenario.mjs'],
      {
        cwd: repoRoot,
        env: { ...process.env, AIOPS_BASE_URL: baseUrl, ...extraEnv },
        stdio: ['ignore', 'pipe', 'pipe'],
      }
    )
    let stdout = ''
    let stderr = ''
    const watchdog = setTimeout(() => child.kill(), watchdogMs)
    child.stdout.setEncoding('utf8')
    child.stdout.on('data', (chunk) => {
      stdout += chunk
    })
    child.stderr.setEncoding('utf8')
    child.stderr.on('data', (chunk) => {
      stderr += chunk
    })
    child.once('error', reject)
    child.once('close', (exitCode) => {
      clearTimeout(watchdog)
      resolve({ exitCode, stdout, stderr })
    })
  })
}

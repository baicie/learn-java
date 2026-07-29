#!/usr/bin/env node
/**
 * Phase Z9 Zabbix MVP Demo Script
 * 完整演示 Zabbix Webhook -> AlertEvent -> Incident -> Evidence -> RCA -> AI -> Report 流程
 */

import { randomBytes } from 'node:crypto'

const BASE_URL = process.env.AIOPS_BASE_URL || 'http://localhost:8080'
const ZABBIX_ENDPOINT =
  process.env.AIOPS_ZABBIX_ENDPOINT || 'http://localhost:8081/api_jsonrpc.php'
const ZABBIX_USERNAME = process.env.AIOPS_ZABBIX_USERNAME || 'Admin'
const ZABBIX_PASSWORD = process.env.AIOPS_ZABBIX_PASSWORD || 'zabbix'
const ADMIN_USERNAME = process.env.AIOPS_USERNAME || 'admin'
const ADMIN_PASSWORD = process.env.AIOPS_PASSWORD || 'admin123'
const REQUEST_TIMEOUT_MS = positiveInteger(
  process.env.AIOPS_HTTP_TIMEOUT_MS,
  30_000
)
const DEMO_EVENTS = createDemoEvents()

let authToken = null
let realTenantId = null
let dataSourceId = null
let webhookToken = null
let incidentId = null

function createDemoEvents() {
  // Keep Zabbix IDs numeric while reserving the final digit for this run's four events.
  const randomRunId = BigInt(`0x${randomBytes(7).toString('hex')}`)
  const eventIdBase = ((1n << 56n) + randomRunId) * 10n
  return [
    ['30001', 'CPU High', 'high'],
    ['30002', 'API Slow', 'average'],
    ['30003', 'Health Check Failed', 'disaster'],
    ['30004', 'Error Log Increased', 'warning'],
  ].map(([triggerId, title, severity], index) => [
    (eventIdBase + BigInt(index + 1)).toString(),
    triggerId,
    title,
    severity,
  ])
}

function positiveInteger(value, fallback) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function nonBlank(value) {
  return typeof value === 'string' && value.trim().length > 0
}

async function request(method, path, body, headers = {}) {
  const url = `${BASE_URL}${path}`
  const defaultHeaders = {
    'Content-Type': 'application/json',
  }
  if (realTenantId) defaultHeaders['X-Tenant-Id'] = realTenantId
  const opts = {
    method,
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    headers: {
      ...defaultHeaders,
      ...headers,
    },
  }
  if (body !== undefined) opts.body = JSON.stringify(body)
  let res
  try {
    res = await fetch(url, opts)
  } catch (error) {
    if (error?.name === 'TimeoutError') {
      throw new Error(`Request timed out after ${REQUEST_TIMEOUT_MS}ms: ${url}`)
    }
    throw error
  }
  const text = await res.text()
  let json
  try {
    json = JSON.parse(text)
  } catch {
    json = { raw: text }
  }
  return { status: res.status, ok: res.ok, data: json }
}

async function login() {
  console.log('\n[Auth] Logging in as admin...')
  const res = await request('POST', '/api/auth/login', {
    username: ADMIN_USERNAME,
    password: ADMIN_PASSWORD,
  })
  if (!res.ok || !res.data.data?.token) {
    throw new Error(`Login failed: ${JSON.stringify(res.data)}`)
  }
  authToken = res.data.data.token
  realTenantId = res.data.data.user?.tenantId || 'tenant_default'
  console.log(`[Auth] Logged in. Tenant: ${realTenantId}`)
  return authToken
}

async function apiGet(path) {
  return request('GET', path, undefined, {
    Authorization: `Bearer ${authToken}`,
  })
}

async function apiPost(path, body) {
  return request('POST', path, body, { Authorization: `Bearer ${authToken}` })
}

async function createZabbixDatasource() {
  console.log('\n[Datasource] Creating Zabbix datasource...')
  // Check if there's already a zabbix datasource
  const listRes = await apiGet('/api/datasources')
  if (!listRes.ok) {
    throw new Error(`Datasource list failed: ${JSON.stringify(listRes.data)}`)
  }
  const listData = listRes.data.data || []
  const items = Array.isArray(listData)
    ? listData
    : listData.items || listData.content || []
  const existing = items.find(
    (d) => d.type === 'zabbix' && d.endpoint === ZABBIX_ENDPOINT
  )
  if (existing) {
    dataSourceId = existing.id
    console.log(
      `[Datasource] Using existing datasource: ${dataSourceId} (${existing.name})`
    )
    return dataSourceId
  }

  const res = await apiPost('/api/datasources', {
    type: 'zabbix',
    name: 'Demo Zabbix',
    zabbix: {
      endpoint: ZABBIX_ENDPOINT,
      username: ZABBIX_USERNAME,
      password: ZABBIX_PASSWORD,
      connectTimeoutSeconds: 10,
      readTimeoutSeconds: 30,
    },
  })
  if (!res.ok) {
    throw new Error(`Datasource create failed: ${JSON.stringify(res.data)}`)
  }
  dataSourceId = res.data.data.id
  console.log(`[Datasource] Created: ${dataSourceId} (${res.data.data.name})`)
  return dataSourceId
}

async function activateZabbixDatasource() {
  const id = encodeURIComponent(dataSourceId)
  const res = await apiPost(`/api/datasources/${id}/test`, {})
  if (!res.ok || res.data.data?.ok !== true) {
    throw new Error(
      `Datasource connection test failed: ${JSON.stringify(res.data)}`
    )
  }
  console.log(`[Datasource] Connection verified: ${dataSourceId}`)
}

async function issueWebhookToken() {
  const id = encodeURIComponent(dataSourceId)
  const res = await apiGet(`/api/datasources/${id}/zabbix-webhook-token`)
  webhookToken = res.data.data?.token
  if (
    !res.ok ||
    typeof webhookToken !== 'string' ||
    !webhookToken.startsWith('zwh_')
  ) {
    throw new Error(`Webhook token issue failed: ${JSON.stringify(res.data)}`)
  }
  console.log(`[Datasource] Datasource-scoped webhook token issued`)
}

async function ingestWebhook(eventId, triggerId, title, severity, startsAt) {
  const body = {
    datasourceId: dataSourceId,
    eventId,
    problemId: eventId,
    triggerId,
    objectId: triggerId,
    status: 'PROBLEM',
    eventValue: '1',
    severity,
    title,
    message: `${title} on order-service`,
    hostId: '10084',
    hostName: 'aiops-demo-host',
    app: 'mall',
    env: 'demo',
    service: 'order-service',
    endpoint: '/api/order/create',
    startsAt,
    tags: { service: 'order-service', env: 'demo' },
  }
  const res = await request(
    'POST',
    `/api/integrations/zabbix/events?datasourceId=${dataSourceId}`,
    body,
    { 'X-AegisOps-Webhook-Token': webhookToken, 'X-Tenant-Id': realTenantId }
  )
  if (res.ok) {
    const alertId = res.data.data?.alertId || res.data.data?.id || 'ok'
    console.log(`  [Webhook] ${title} -> alertId=${alertId}`)
  } else {
    throw new Error(
      `Webhook ${title} failed: ${res.data.message || res.data.errorCode || res.data.raw}`
    )
  }
  return res
}

async function aggregateIncidents() {
  console.log('\n[Incident] Running aggregation...')
  const res = await apiPost('/api/incidents/aggregate', {
    windowMinutes: 1440,
    limit: 1000,
  })
  if (!res.ok) {
    throw new Error(
      `Incident aggregation failed: ${res.data.message || res.data.errorCode || res.status}`
    )
  }
  const data = res.data.data || {}
  console.log(
    `  [Incident] Created=${data.incidentsCreated || 0}, updated=${data.incidentsUpdated || 0}, linked=${data.alertsLinked || 0}`
  )
  return res
}

async function getIncidentIdForEvents(eventIds) {
  const res = await apiGet('/api/incidents')
  if (!res.ok || !res.data.data) {
    throw new Error(
      `Incident list failed: ${res.data.message || res.data.errorCode || res.status}`
    )
  }
  const listData = res.data.data
  const items = Array.isArray(listData)
    ? listData
    : listData.items || listData.content || []
  const expectedSourceIds = new Set(
    eventIds.map((eventId) => `${dataSourceId}:${eventId}`)
  )

  for (const incident of items) {
    if (!incident?.id) continue
    const id = encodeURIComponent(incident.id)
    const alertsRes = await apiGet(`/api/incidents/${id}/alerts`)
    if (!alertsRes.ok || !alertsRes.data.data) {
      throw new Error(
        `Incident alert lookup failed for ${incident.id}: ${alertsRes.data.message || alertsRes.data.errorCode || alertsRes.status}`
      )
    }
    const alertData = alertsRes.data.data
    const alerts = Array.isArray(alertData)
      ? alertData
      : alertData.items || alertData.content || []
    const sourceIds = new Set(alerts.map((alert) => alert.sourceEventId))
    if ([...expectedSourceIds].every((sourceId) => sourceIds.has(sourceId))) {
      return incident.id
    }
  }
  return null
}

async function collectEvidence() {
  console.log(`\n[Evidence] Collecting evidence for incident ${incidentId}...`)
  const res = await apiPost(
    `/api/incidents/${incidentId}/evidence/zabbix/collect`,
    {
      lookbackMinutes: 30,
    }
  )
  if (!res.ok) {
    throw new Error(
      `Evidence collection failed: ${res.data.message || res.data.errorCode || res.status}`
    )
  }
  const data = res.data.data || {}
  const evidenceCreated = Number(data.evidenceCreated || 0)
  const evidenceUpdated = Number(data.evidenceUpdated || 0)
  if (evidenceCreated + evidenceUpdated < 1) {
    throw new Error('Evidence collection returned no evidence')
  }
  console.log(
    `  [Evidence] Created=${evidenceCreated}, updated=${evidenceUpdated}`
  )
  return res
}

async function runRCA() {
  console.log(`\n[RCA] Running analysis for incident ${incidentId}...`)
  const res = await apiPost(`/api/incidents/${incidentId}/rca/analyze`, {
    force: true,
  })
  if (!res.ok) {
    throw new Error(
      `RCA analysis failed: ${res.data.message || res.data.errorCode || res.status}`
    )
  }
  const d = res.data.data || {}
  if (
    !nonBlank(d.suspectedRootCause) ||
    !Array.isArray(d.matchedRules) ||
    d.matchedRules.length === 0
  ) {
    throw new Error('RCA analysis returned no root cause or rules')
  }
  console.log(`  [RCA] RootCause: ${d.suspectedRootCause}`)
  console.log(`  [RCA] Confidence: ${d.confidence ?? 'n/a'}`)
  console.log(`  [RCA] MatchedRules: ${d.matchedRules.join(', ')}`)
  return res
}

async function runAIDiagnosis() {
  console.log(`\n[AI] Running diagnosis for incident ${incidentId}...`)
  const res = await apiPost(`/api/incidents/${incidentId}/ai/diagnose`, {
    force: true,
    locale: 'zh-CN',
  })
  if (!res.ok) {
    throw new Error(
      `AI diagnosis failed: ${res.data.message || res.data.errorCode || res.data.raw || res.status}`
    )
  }
  const d = res.data.data || {}
  const summary = d.summary || d.summaryText
  if (!nonBlank(summary) || !nonBlank(d.rootCause) || !nonBlank(d.impact)) {
    throw new Error('AI diagnosis returned incomplete content')
  }
  console.log(`  [AI] Summary: ${summary}`)
  console.log(`  [AI] RootCause: ${d.rootCause}`)
  console.log(`  [AI] Impact: ${d.impact}`)
  return res
}

async function generateReport() {
  console.log(
    `\n[Report] Generating markdown report for incident ${incidentId}...`
  )
  const res = await apiPost(`/api/incidents/${incidentId}/reports`, {
    force: true,
    locale: 'zh-CN',
    createdBy: 'demo-zabbix-scenario',
  })
  if (!res.ok) {
    throw new Error(
      `Report generation failed: ${res.data.message || res.data.errorCode || res.status}`
    )
  }
  const d = res.data.data || {}
  if (!d.id) {
    throw new Error('Report generation returned no report id')
  }
  const md = d.markdownContent || ''
  const requiredSections = ['\u6545\u969c\u62a5\u544a', '\u5173\u952e\u8bc1\u636e', 'AI \u8bca\u65ad']
  if (!requiredSections.every((section) => md.includes(section))) {
    throw new Error('Report markdown is missing required sections')
  }
  console.log(
    `  [Report] Created: id=${d.id}, title="${d.title}", version=${d.versionNo}`
  )
  const lines = md.split('\n').slice(0, 80)
  console.log('\n  [Report] Markdown preview (first 80 lines):')
  lines.forEach((l) => console.log(`    ${l}`))
  return res
}

async function main() {
  console.log('='.repeat(60))
  console.log('  Phase Z9 Zabbix MVP Demo')
  console.log('='.repeat(60))
  console.log(`BASE_URL=${BASE_URL}`)
  console.log(`ZABBIX_ENDPOINT=${ZABBIX_ENDPOINT}`)

  await login()
  await createZabbixDatasource()
  await activateZabbixDatasource()
  await issueWebhookToken()
  console.log(`TENANT_ID=${realTenantId}`)
  console.log(`DATASOURCE_ID=${dataSourceId}`)

  console.log('\n' + '='.repeat(60))
  console.log('Step 1: Ingest Zabbix Webhook Events')
  console.log('='.repeat(60))
  const eventStartedAt = new Date().toISOString()
  for (const event of DEMO_EVENTS) {
    await ingestWebhook(...event, eventStartedAt)
  }

  await aggregateIncidents()

  incidentId = await getIncidentIdForEvents(
    DEMO_EVENTS.map(([eventId]) => eventId)
  )
  if (!incidentId) {
    throw new Error(
      'No incident found after aggregation. No incident contains all injected Zabbix events.'
    )
  }
  console.log(`\n[Incident] Matched incident ID: ${incidentId}`)

  await collectEvidence()
  await runRCA()
  await runAIDiagnosis()
  await generateReport()

  console.log('\n' + '='.repeat(60))
  console.log('  Z9 Demo Complete!')
  console.log('='.repeat(60))
}

main().catch((err) => {
  console.error('\n[ERROR]', err.message)
  process.exitCode = 1
})

import axios, { type AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api-client'
import {
  analyzeIncidentRca,
  closeIncident,
  diagnoseIncident,
  generateIncidentReport,
  getIncident,
  getLatestAiDiagnosis,
  getLatestIncidentReport,
  getLatestRca,
  listAlerts,
  listIncidentAlerts,
  listIncidentEvidence,
  listIncidentTimeline,
  listIncidents,
  resolveIncident,
} from './operations-api'

vi.mock('@/lib/api-client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}))

const occurredAt = '2026-08-03T05:30:00Z'
const updatedAt = '2026-08-03T06:00:00Z'

const alert = {
  id: 'alt-1',
  tenantId: 'tenant-1',
  source: 'zabbix',
  severity: 'high',
  title: 'CPU usage is high',
  status: 'resolved',
  startsAt: occurredAt,
  createdAt: occurredAt,
}

const incident = {
  id: 'inc-1',
  tenantId: 'tenant-1',
  title: 'Host CPU incident',
  summary: 'Four Zabbix alerts were correlated.',
  severity: 'high',
  status: 'resolved',
  source: 'system',
  primaryAssetId: 'asset-1',
  aggregationKey: 'zabbix:host-1',
  alertCount: 4,
  startedAt: occurredAt,
  detectedAt: occurredAt,
  lastSeenAt: updatedAt,
  resolvedAt: updatedAt,
  createdAt: occurredAt,
  updatedAt,
}

const incidentAlert = {
  id: 'alt-1',
  source: 'zabbix',
  sourceEventId: 'zabbix:event:51',
  severity: 'high',
  title: 'CPU usage is high',
  status: 'resolved',
  assetId: 'asset-1',
  entityName: 'host-1',
  fingerprint: 'zabbix:host-1:cpu',
  startsAt: occurredAt,
  relationType: 'primary',
}

const timelineEntry = {
  id: 'tl-1',
  eventTime: occurredAt,
  eventType: 'alert_linked',
  title: 'CPU usage is high',
  description: 'Zabbix event 51',
  source: 'system',
  payloadJson: '{}',
}

const incidentDetail = {
  incident,
  alerts: [incidentAlert],
  timeline: [timelineEntry],
}

const evidence = {
  id: 'evd-1',
  tenantId: 'tenant-1',
  incidentId: 'inc-1',
  evidenceKey: 'zabbix:cpu:item-cpu',
  source: 'zabbix',
  evidenceType: 'metric_cpu_high',
  title: 'CPU usage remained high',
  summary: 'Maximum CPU usage was 95%.',
  timeRangeStart: occurredAt,
  timeRangeEnd: updatedAt,
  confidence: 0.86,
  payloadJson: '{}',
  createdAt: updatedAt,
  updatedAt,
}

const rca = {
  id: 'rca-1',
  incidentId: 'inc-1',
  status: 'completed',
  suspectedRootCause: 'Sustained CPU saturation',
  confidence: 0.95,
  summary: 'Multiple signals identify CPU saturation.',
  evidence: [
    {
      ruleId: 'host-cpu-high',
      title: 'Host CPU high',
      description: 'CPU evidence crossed the rule threshold.',
      score: 0.9,
      confidence: 0.95,
      attributes: { evidenceRefs: ['zabbix:cpu:item-cpu'] },
    },
  ],
  matchedRules: ['host-cpu-high'],
  evidenceRefs: ['zabbix:cpu:item-cpu'],
  modelVersion: 'rules-v2-evidence',
  createdAt: updatedAt,
}

const diagnosis = {
  id: 'diag-1',
  incidentId: 'inc-1',
  status: 'completed',
  provider: 'aiops-agent',
  model: 'langgraph-deterministic',
  agentName: 'aegisops_diagnosis_graph',
  summary: 'CPU saturation caused service degradation.',
  rootCause: 'Sustained CPU saturation',
  impact: 'Requests were slower.',
  nextSteps: ['Inspect top CPU consumers.'],
  runbookSuggestions: ['Host inspection'],
  risks: ['Do not restart without approval.'],
  matchedRules: ['host-cpu-high'],
  evidenceRefs: ['zabbix:cpu:item-cpu'],
  timeline: [{ eventType: 'alert_linked', title: 'CPU usage is high' }],
  raw: { generationMode: 'deterministic' },
  createdAt: updatedAt,
}

const report = {
  id: 'rpt-1',
  incidentId: 'inc-1',
  versionNo: 1,
  reportType: 'incident_markdown',
  format: 'markdown',
  title: 'Host CPU incident report',
  markdownContent: '# Host CPU incident',
  snapshotJson: '{}',
  createdBy: 'system',
  createdAt: updatedAt,
  updatedAt,
}

function response(data: unknown) {
  return { data: { success: true, data } }
}

function missingResource(errorCode: string) {
  const error = new axios.AxiosError('request failed')
  error.response = {
    data: { success: false, errorCode, message: 'not generated' },
    status: 400,
    statusText: 'Bad Request',
    headers: {},
    config: { headers: new axios.AxiosHeaders() },
  } as AxiosResponse
  return error
}

describe('operations API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads typed alert and incident lists', async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce(response([alert]))
      .mockResolvedValueOnce(response([incident]))

    await expect(listAlerts()).resolves.toEqual([alert])
    await expect(listIncidents()).resolves.toEqual([incident])

    expect(apiClient.get).toHaveBeenNthCalledWith(1, '/api/alerts')
    expect(apiClient.get).toHaveBeenNthCalledWith(2, '/api/incidents')
  })

  it('loads the incident core, alerts, and timeline endpoints', async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce(response(incidentDetail))
      .mockResolvedValueOnce(response([incidentAlert]))
      .mockResolvedValueOnce(response([timelineEntry]))

    await expect(getIncident('inc-1')).resolves.toEqual(incidentDetail)
    await expect(listIncidentAlerts('inc-1')).resolves.toEqual([incidentAlert])
    await expect(listIncidentTimeline('inc-1')).resolves.toEqual([
      timelineEntry,
    ])

    expect(apiClient.get).toHaveBeenNthCalledWith(1, '/api/incidents/inc-1')
    expect(apiClient.get).toHaveBeenNthCalledWith(
      2,
      '/api/incidents/inc-1/alerts'
    )
    expect(apiClient.get).toHaveBeenNthCalledWith(
      3,
      '/api/incidents/inc-1/timeline'
    )
  })

  it('loads evidence, RCA, AI diagnosis, and report endpoints', async () => {
    vi.mocked(apiClient.get)
      .mockResolvedValueOnce(response([evidence]))
      .mockResolvedValueOnce(response(rca))
      .mockResolvedValueOnce(response(diagnosis))
      .mockResolvedValueOnce(response(report))

    await expect(listIncidentEvidence('inc-1')).resolves.toEqual([evidence])
    await expect(getLatestRca('inc-1')).resolves.toEqual(rca)
    await expect(getLatestAiDiagnosis('inc-1')).resolves.toEqual(diagnosis)
    await expect(getLatestIncidentReport('inc-1')).resolves.toEqual(report)

    expect(apiClient.get).toHaveBeenNthCalledWith(
      1,
      '/api/incidents/inc-1/evidence'
    )
    expect(apiClient.get).toHaveBeenNthCalledWith(
      2,
      '/api/incidents/inc-1/rca/latest'
    )
    expect(apiClient.get).toHaveBeenNthCalledWith(
      3,
      '/api/incidents/inc-1/ai/latest'
    )
    expect(apiClient.get).toHaveBeenNthCalledWith(
      4,
      '/api/incidents/inc-1/reports/latest'
    )
  })

  it('returns null when latest derived resources have not been generated', async () => {
    vi.mocked(apiClient.get)
      .mockRejectedValueOnce(missingResource('RCA_NOT_FOUND'))
      .mockRejectedValueOnce(missingResource('AI_DIAGNOSIS_NOT_FOUND'))
      .mockRejectedValueOnce(missingResource('INCIDENT_REPORT_NOT_FOUND'))

    await expect(getLatestRca('inc-1')).resolves.toBeNull()
    await expect(getLatestAiDiagnosis('inc-1')).resolves.toBeNull()
    await expect(getLatestIncidentReport('inc-1')).resolves.toBeNull()
  })

  it('does not hide unrelated latest-resource failures', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(
      missingResource('INCIDENT_NOT_FOUND')
    )

    await expect(getLatestRca('missing')).rejects.toThrow('request failed')
  })

  it('posts typed diagnose, analyze, report, resolve, and close actions', async () => {
    vi.mocked(apiClient.post)
      .mockResolvedValueOnce(response(rca))
      .mockResolvedValueOnce(response(diagnosis))
      .mockResolvedValueOnce(response(report))
      .mockResolvedValueOnce(response(incident))
      .mockResolvedValueOnce(response({ ...incident, status: 'closed' }))

    await expect(analyzeIncidentRca('inc-1', { force: true })).resolves.toEqual(
      rca
    )
    await expect(
      diagnoseIncident('inc-1', { force: true, locale: 'zh-CN' })
    ).resolves.toEqual(diagnosis)
    await expect(
      generateIncidentReport('inc-1', {
        force: true,
        locale: 'zh-CN',
        createdBy: 'admin',
      })
    ).resolves.toEqual(report)
    await expect(resolveIncident('inc-1')).resolves.toEqual(incident)
    await expect(closeIncident('inc-1')).resolves.toMatchObject({
      status: 'closed',
    })

    expect(apiClient.post).toHaveBeenNthCalledWith(
      1,
      '/api/incidents/inc-1/rca/analyze',
      { force: true }
    )
    expect(apiClient.post).toHaveBeenNthCalledWith(
      2,
      '/api/incidents/inc-1/ai/diagnose',
      { force: true, locale: 'zh-CN' }
    )
    expect(apiClient.post).toHaveBeenNthCalledWith(
      3,
      '/api/incidents/inc-1/reports',
      { force: true, locale: 'zh-CN', createdBy: 'admin' }
    )
    expect(apiClient.post).toHaveBeenNthCalledWith(
      4,
      '/api/incidents/inc-1/resolve'
    )
    expect(apiClient.post).toHaveBeenNthCalledWith(
      5,
      '/api/incidents/inc-1/close'
    )
  })

  it('rejects malformed operation payloads', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      response([{ ...alert, startsAt: 1 }])
    )

    await expect(listAlerts()).rejects.toThrow()
  })
})

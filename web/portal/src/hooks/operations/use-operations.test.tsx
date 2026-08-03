import { createElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { AuthorizationPrincipal } from '@/auth/authorization-types'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
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
  listIncidentEvidence,
  listIncidents,
  resolveIncident,
} from '@/api/operations/operations-api'
import { operationsKeys } from '@/api/operations/query-keys'
import { useAuthStore } from '@/stores/auth-store'
import {
  useAnalyzeIncidentRca,
  useAlerts,
  useCloseIncident,
  useDiagnoseIncident,
  useGenerateIncidentReport,
  useIncidentOperationsDetail,
  useIncidents,
  useResolveIncident,
} from './use-operations'

vi.mock('@/api/operations/operations-api', () => ({
  analyzeIncidentRca: vi.fn(),
  closeIncident: vi.fn(),
  diagnoseIncident: vi.fn(),
  generateIncidentReport: vi.fn(),
  getIncident: vi.fn(),
  getLatestAiDiagnosis: vi.fn(),
  getLatestIncidentReport: vi.fn(),
  getLatestRca: vi.fn(),
  listAlerts: vi.fn(),
  listIncidentAlerts: vi.fn(),
  listIncidentEvidence: vi.fn(),
  listIncidentTimeline: vi.fn(),
  listIncidents: vi.fn(),
  resolveIncident: vi.fn(),
}))

const occurredAt = '2026-08-03T05:30:00Z'
const updatedAt = '2026-08-03T06:00:00Z'

function principal(tenantId: string): AuthorizationPrincipal {
  return {
    userId: `user-${tenantId}`,
    tenantId,
    username: `admin-${tenantId}`,
    displayName: `Admin ${tenantId}`,
    roles: ['system_admin'],
    permissions: ['alert:read', 'incident:read'],
    dataScopes: {},
  }
}

function alert(tenantId: string) {
  return {
    id: `alert-${tenantId}`,
    tenantId,
    source: 'zabbix',
    severity: 'high',
    title: `CPU alert for ${tenantId}`,
    status: 'open',
    startsAt: occurredAt,
    createdAt: occurredAt,
  }
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

const detail = {
  incident,
  alerts: [],
  timeline: [],
}

const rca = {
  id: 'rca-1',
  incidentId: 'inc-1',
  status: 'completed',
  suspectedRootCause: 'Sustained CPU saturation',
  confidence: 0.95,
  summary: 'Multiple signals identify CPU saturation.',
  evidence: [],
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
  nextSteps: [],
  runbookSuggestions: [],
  risks: [],
  matchedRules: [],
  evidenceRefs: [],
  timeline: [],
  raw: {},
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

describe('operations query hooks', () => {
  let client: QueryClient

  beforeEach(() => {
    vi.clearAllMocks()
    client = new QueryClient({
      defaultOptions: {
        queries: { retry: false, staleTime: Infinity },
        mutations: { retry: false },
      },
    })

    useAuthStore.getState().auth.setPrincipal(principal('tenant-1'))
    useAuthStore.getState().auth.setAuthorizationLoaded(true)

    vi.mocked(getIncident).mockResolvedValue(detail)
    vi.mocked(listIncidentEvidence).mockResolvedValue([])
    vi.mocked(getLatestRca).mockResolvedValue(null)
    vi.mocked(getLatestAiDiagnosis).mockResolvedValue(diagnosis)
    vi.mocked(getLatestIncidentReport).mockResolvedValue(null)
  })

  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client }, children)

  it('loads core detail and derived resources through independent queries', async () => {
    const { result } = await renderHook(
      () => useIncidentOperationsDetail('inc-1'),
      { wrapper }
    )

    await expect.poll(() => result.current.isPending).toBe(false)

    expect(result.current.detail.data).toEqual(detail)
    expect(result.current.evidence.data).toEqual([])
    expect(result.current.rca.data).toBeNull()
    expect(result.current.aiDiagnosis.data).toEqual(diagnosis)
    expect(result.current.report.data).toBeNull()
    expect(
      client.getQueryData(operationsKeys.incident('tenant-1', 'inc-1'))
    ).toEqual(detail)
    expect(
      client.getQueryData(operationsKeys.incidentRca('tenant-1', 'inc-1'))
    ).toBeNull()
  })

  it('does not start operations queries until a principal is loaded', async () => {
    useAuthStore.getState().auth.setPrincipal(null)
    useAuthStore.getState().auth.setAuthorizationLoaded(false)

    const { result } = await renderHook(
      () => ({
        alerts: useAlerts(),
        incidents: useIncidents(),
        detail: useIncidentOperationsDetail('inc-1'),
      }),
      { wrapper }
    )

    await expect.poll(() => result.current.alerts.fetchStatus).toBe('idle')
    expect(result.current.alerts.data).toBeUndefined()
    expect(result.current.incidents.data).toBeUndefined()
    expect(result.current.detail.detail.data).toBeUndefined()
    expect(listAlerts).not.toHaveBeenCalled()
    expect(listIncidents).not.toHaveBeenCalled()
    expect(getIncident).not.toHaveBeenCalled()
    expect(listIncidentEvidence).not.toHaveBeenCalled()
    expect(getLatestRca).not.toHaveBeenCalled()
    expect(getLatestAiDiagnosis).not.toHaveBeenCalled()
    expect(getLatestIncidentReport).not.toHaveBeenCalled()
  })

  it('loads a fresh cache partition after the principal tenant changes', async () => {
    vi.mocked(listAlerts)
      .mockResolvedValueOnce([alert('tenant-1')])
      .mockResolvedValueOnce([alert('tenant-2')])

    const first = await renderHook(() => useAlerts(), { wrapper })
    await expect
      .poll(() => first.result.current.data)
      .toEqual([alert('tenant-1')])
    await first.unmount()

    useAuthStore.getState().auth.setPrincipal(principal('tenant-2'))

    const second = await renderHook(() => useAlerts(), { wrapper })
    await expect
      .poll(() => second.result.current.data)
      .toEqual([alert('tenant-2')])

    expect(listAlerts).toHaveBeenCalledTimes(2)
    expect(client.getQueryData(operationsKeys.alerts('tenant-1'))).toEqual([
      alert('tenant-1'),
    ])
    expect(client.getQueryData(operationsKeys.alerts('tenant-2'))).toEqual([
      alert('tenant-2'),
    ])
  })

  it('invalidates incident queries after diagnosis and lifecycle actions', async () => {
    vi.mocked(analyzeIncidentRca).mockResolvedValue(rca)
    vi.mocked(diagnoseIncident).mockResolvedValue(diagnosis)
    vi.mocked(generateIncidentReport).mockResolvedValue(report)
    vi.mocked(resolveIncident).mockResolvedValue(incident)
    vi.mocked(closeIncident).mockResolvedValue({
      ...incident,
      status: 'closed',
    })
    const invalidate = vi.spyOn(client, 'invalidateQueries')

    const { result } = await renderHook(
      () => ({
        analyze: useAnalyzeIncidentRca('inc-1'),
        diagnose: useDiagnoseIncident('inc-1'),
        report: useGenerateIncidentReport('inc-1'),
        resolve: useResolveIncident('inc-1'),
        close: useCloseIncident('inc-1'),
      }),
      { wrapper }
    )

    await result.current.analyze.mutateAsync({ force: true })
    await result.current.diagnose.mutateAsync({ locale: 'zh-CN' })
    await result.current.report.mutateAsync({ createdBy: 'admin' })
    await result.current.resolve.mutateAsync()
    await result.current.close.mutateAsync()

    expect(analyzeIncidentRca).toHaveBeenCalledWith('inc-1', { force: true })
    expect(diagnoseIncident).toHaveBeenCalledWith('inc-1', {
      locale: 'zh-CN',
    })
    expect(generateIncidentReport).toHaveBeenCalledWith('inc-1', {
      createdBy: 'admin',
    })
    expect(resolveIncident).toHaveBeenCalledWith('inc-1')
    expect(closeIncident).toHaveBeenCalledWith('inc-1')
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: operationsKeys.incident('tenant-1', 'inc-1'),
    })
    expect(invalidate).toHaveBeenCalledWith({
      exact: true,
      queryKey: operationsKeys.incidents('tenant-1'),
    })
  })
})

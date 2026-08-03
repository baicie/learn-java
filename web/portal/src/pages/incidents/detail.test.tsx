import '@/styles/index.css'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { page } from 'vitest/browser'
import { useAuthStore } from '@/stores/auth-store'
import { IncidentDetailPage } from './detail'

const hooks = vi.hoisted(() => ({
  useAnalyzeIncidentRca: vi.fn(),
  useCloseIncident: vi.fn(),
  useDiagnoseIncident: vi.fn(),
  useGenerateIncidentReport: vi.fn(),
  useIncidentOperationsDetail: vi.fn(),
  useResolveIncident: vi.fn(),
}))

const toast = vi.hoisted(() => ({ success: vi.fn() }))

vi.mock('@/hooks/operations/use-operations', () => hooks)
vi.mock('sonner', () => ({ toast }))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: React.ReactNode }) => (
    <a href='/incidents'>{children}</a>
  ),
}))

vi.mock('@/components/feedback/confirm-provider', () => ({
  useConfirm: () => vi.fn(async () => true),
}))

vi.mock('@/components/layout/header', () => ({
  Header: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

vi.mock('@/components/layout/main', () => ({
  Main: ({ children }: { children: React.ReactNode }) => (
    <main>{children}</main>
  ),
}))

vi.mock('@/components/profile-dropdown', () => ({
  ProfileDropdown: () => null,
}))

vi.mock('@/components/search', () => ({ Search: () => null }))
vi.mock('@/components/theme-switch', () => ({ ThemeSwitch: () => null }))

const occurredAt = '2026-08-03T05:30:00Z'
const updatedAt = '2026-08-03T06:00:00Z'

const detail = {
  incident: {
    id: 'inc-1',
    tenantId: 'default',
    title: 'Zabbix CPU saturation',
    summary: 'Multiple host signals were correlated.',
    severity: 'critical',
    status: 'resolved',
    source: 'zabbix',
    primaryAssetId: 'asset-1',
    aggregationKey: 'zabbix:host-1',
    alertCount: 2,
    startedAt: occurredAt,
    detectedAt: occurredAt,
    lastSeenAt: updatedAt,
    resolvedAt: updatedAt,
    createdAt: occurredAt,
    updatedAt,
  },
  alerts: [
    {
      id: 'alt-1',
      source: 'zabbix',
      sourceEventId: '51',
      severity: 'disaster',
      title: 'CPU usage is high',
      status: 'open',
      assetId: 'asset-1',
      entityName: 'zabbix-host',
      fingerprint: 'zabbix:host-1:cpu',
      startsAt: occurredAt,
      relationType: 'primary',
    },
  ],
  timeline: [
    {
      id: 'tl-1',
      eventTime: occurredAt,
      eventType: 'alert_linked',
      title: 'Alert linked to incident',
      description: 'Zabbix event 51 was correlated.',
      source: 'system',
      payloadJson: '{}',
    },
  ],
}

const idleMutation = () => ({ isPending: false, mutate: vi.fn() })

describe('IncidentDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().auth.setPrincipal({
      userId: 'admin',
      tenantId: 'default',
      username: 'admin',
      displayName: 'Admin',
      roles: [],
      permissions: ['incident:read', 'incident:diagnose', 'incident:write'],
      dataScopes: {},
    })

    hooks.useAnalyzeIncidentRca.mockReturnValue(idleMutation())
    hooks.useCloseIncident.mockReturnValue(idleMutation())
    hooks.useDiagnoseIncident.mockReturnValue(idleMutation())
    hooks.useGenerateIncidentReport.mockReturnValue(idleMutation())
    hooks.useResolveIncident.mockReturnValue(idleMutation())
    hooks.useIncidentOperationsDetail.mockReturnValue({
      detail: {
        data: detail,
        error: null,
        isError: false,
        isPending: false,
        refetch: vi.fn(),
      },
      evidence: {
        data: [
          {
            id: 'evd-1',
            tenantId: 'default',
            incidentId: 'inc-1',
            evidenceKey: 'zabbix:item:cpu',
            source: 'zabbix',
            evidenceType: 'metric_cpu_high',
            title: 'CPU remained above threshold',
            summary: 'Maximum CPU usage reached 95%.',
            timeRangeStart: occurredAt,
            timeRangeEnd: updatedAt,
            confidence: 0.91,
            payloadJson: '{}',
            createdAt: updatedAt,
            updatedAt,
          },
        ],
        error: null,
        isError: false,
        isPending: false,
        refetch: vi.fn(),
      },
      rca: {
        data: {
          id: 'rca-1',
          incidentId: 'inc-1',
          status: 'completed',
          suspectedRootCause: 'Sustained CPU saturation',
          confidence: 0.95,
          summary: 'CPU and service evidence matched.',
          evidence: [],
          matchedRules: ['HOST_CPU_HIGH'],
          evidenceRefs: ['zabbix:item:cpu'],
          modelVersion: 'rules-v2-evidence',
          createdAt: updatedAt,
        },
        error: null,
        isError: false,
        isPending: false,
        refetch: vi.fn(),
      },
      aiDiagnosis: {
        data: {
          id: 'diag-1',
          incidentId: 'inc-1',
          status: 'completed',
          provider: 'aiops-agent',
          model: 'langgraph-deterministic',
          agentName: 'aegisops_diagnosis_graph',
          summary: 'CPU saturation caused service degradation.',
          rootCause: 'A CPU-intensive process exhausted capacity.',
          impact: 'Requests became slower.',
          nextSteps: ['Inspect top CPU consumers.'],
          runbookSuggestions: ['Host inspection'],
          risks: ['Restart requires approval.'],
          matchedRules: ['HOST_CPU_HIGH'],
          evidenceRefs: ['zabbix:item:cpu'],
          timeline: [],
          raw: {},
          createdAt: updatedAt,
        },
        error: null,
        isError: false,
        isPending: false,
        refetch: vi.fn(),
      },
      report: {
        data: {
          id: 'rpt-1',
          incidentId: 'inc-1',
          versionNo: 1,
          reportType: 'incident_markdown',
          format: 'markdown',
          title: 'CPU incident report',
          markdownContent: '# CPU incident\n\nService recovered.',
          snapshotJson: '{}',
          createdBy: 'admin',
          createdAt: updatedAt,
          updatedAt,
        },
        error: null,
        isError: false,
        isPending: false,
        refetch: vi.fn(),
      },
    })
  })

  afterEach(async () => {
    await page.viewport(1024, 768)
  })

  it('shows the complete evidence-driven incident workspace', async () => {
    const screen = await render(<IncidentDetailPage incidentId='inc-1' />)

    await expect
      .element(screen.getByText('Zabbix CPU saturation'))
      .toBeVisible()
    await expect
      .element(screen.getByText('Alert linked to incident'))
      .toBeVisible()
    await expect.element(screen.getByText('CPU usage is high')).toBeVisible()
    await expect
      .element(screen.getByText('灾难', { exact: true }))
      .toBeVisible()
    await expect
      .element(screen.getByText('进行中', { exact: true }))
      .toBeVisible()
    await expect
      .element(screen.getByText('CPU remained above threshold'))
      .toBeVisible()
    await expect
      .element(screen.getByText('Sustained CPU saturation'))
      .toBeVisible()
    await expect
      .element(screen.getByText('CPU saturation caused service degradation.'))
      .toBeVisible()
    await expect.element(screen.getByText('CPU incident report')).toBeVisible()
  })

  it('offers focused workspace tabs and disables unavailable execution views', async () => {
    const screen = await render(<IncidentDetailPage incidentId='inc-1' />)

    for (const name of [
      '概览',
      '时间线',
      'AI 诊断',
      'Runbook',
      '自动化日志',
      '复盘',
    ]) {
      await expect
        .element(screen.getByRole('tab', { name, exact: true }))
        .toBeVisible()
    }

    await expect
      .element(screen.getByRole('tab', { name: 'Runbook', exact: true }))
      .toBeDisabled()
    await expect
      .element(screen.getByRole('tab', { name: '自动化日志', exact: true }))
      .toBeDisabled()

    await screen.getByRole('tab', { name: '时间线', exact: true }).click()
    await expect
      .element(screen.getByText('按时间排序的 Incident 证据事件。'))
      .toBeVisible()
  })

  it('does not overflow a 390px viewport with long operation identifiers', async () => {
    const longIdentifier = `zabbix:${'a'.repeat(512)}`
    const current = hooks.useIncidentOperationsDetail()
    hooks.useIncidentOperationsDetail.mockReturnValue({
      ...current,
      detail: {
        ...current.detail,
        data: {
          ...current.detail.data,
          incident: {
            ...current.detail.data.incident,
            aggregationKey: longIdentifier,
            summary: `Aggregated alert, aggregationKey=${longIdentifier}`,
          },
          alerts: current.detail.data.alerts.map(
            (alert: (typeof detail.alerts)[number]) => ({
              ...alert,
              fingerprint: longIdentifier,
            })
          ),
        },
      },
    })
    await page.viewport(390, 844)

    await render(<IncidentDetailPage incidentId='inc-1' />)

    const contentWidth = Math.max(
      document.documentElement.scrollWidth,
      document.body.scrollWidth
    )
    expect(contentWidth).toBeLessThanOrEqual(
      document.documentElement.clientWidth
    )
  })

  it('announces diagnosis only after the server confirms success', async () => {
    let onSuccess: (() => void) | undefined
    const mutate = vi.fn(
      (_input: unknown, options: { onSuccess?: () => void } | undefined) => {
        onSuccess = options?.onSuccess
      }
    )
    hooks.useDiagnoseIncident.mockReturnValue({ isPending: false, mutate })
    const screen = await render(<IncidentDetailPage incidentId='inc-1' />)

    await screen.getByRole('button', { name: 'AI 诊断' }).click()

    expect(mutate).toHaveBeenCalledOnce()
    expect(toast.success).not.toHaveBeenCalled()

    onSuccess?.()

    expect(toast.success).toHaveBeenCalledWith('AI 诊断已完成')
  })

  it('keeps the incident visible when derived resources do not exist yet', async () => {
    const current = hooks.useIncidentOperationsDetail()
    hooks.useIncidentOperationsDetail.mockReturnValue({
      ...current,
      evidence: { ...current.evidence, data: [] },
      rca: { ...current.rca, data: null },
      aiDiagnosis: { ...current.aiDiagnosis, data: null },
      report: { ...current.report, data: null },
    })

    const screen = await render(<IncidentDetailPage incidentId='inc-1' />)

    await expect
      .element(screen.getByText('Zabbix CPU saturation'))
      .toBeVisible()
    await expect.element(screen.getByText('暂无 Evidence')).toBeVisible()
    await expect.element(screen.getByText('暂无 RCA 结果')).toBeVisible()
    await expect.element(screen.getByText('暂无 AI 诊断')).toBeVisible()
    await expect.element(screen.getByText('暂无 Report')).toBeVisible()
  })

  it('hides diagnose and lifecycle actions from read-only users', async () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'reader',
      tenantId: 'default',
      username: 'reader',
      displayName: 'Reader',
      roles: [],
      permissions: ['incident:read'],
      dataScopes: {},
    })

    const screen = await render(<IncidentDetailPage incidentId='inc-1' />)

    for (const name of ['RCA 分析', 'AI 诊断', '生成报告', '关闭']) {
      await expect
        .element(screen.getByRole('button', { name }))
        .not.toBeInTheDocument()
    }
  })
})

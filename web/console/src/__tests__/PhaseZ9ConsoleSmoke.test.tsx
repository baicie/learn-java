import '@testing-library/jest-dom'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { IncidentDetailPage } from '../pages/IncidentDetailPage'
import { renderWithRouter } from '../test/test-utils'

const mockIncidentBundle = {
  incident: {
    id: 'inc_z9',
    title: 'order-service 主机与服务异常',
    summary: 'CPU 高位、接口慢、健康检查失败',
    severity: 'critical',
    status: 'open',
    source: 'zabbix',
    alertCount: 4,
    aggregationKey: 'zabbix:ds_zabbix_z9:10084:order-service:demo:202606210510',
  },
  alerts: [
    {
      id: 'alert_cpu',
      title: 'CPU High',
      severity: 'high',
      status: 'open',
      entityName: 'order-service',
      source: 'zabbix',
      fingerprint: 'fp1',
      startsAt: '2026-06-21T05:10:00Z',
      relationType: 'primary',
    },
  ],
  timeline: [
    {
      id: 'tl_1',
      title: 'CPU High',
      eventType: 'alert_linked',
      eventTime: '2026-06-21T05:10:00Z',
      source: 'system',
      description: '',
      payloadJson: '{}',
    },
  ],
  evidence: [
    {
      id: 'evd_cpu',
      evidenceKey: 'evd_cpu',
      evidenceType: 'metric_cpu_high',
      title: 'CPU 使用率持续高位',
      summary: 'CPU 最大值 96%',
    },
  ],
  rca: {
    id: 'rca_z9',
    suspectedRootCause: '疑似 CPU 饱和导致服务响应变慢',
    confidence: 0.88,
    summary: 'RCA matched 3 rules',
    matchedRules: ['CPU_API_HEALTH_COMBINED'],
    evidenceRefs: ['evd_cpu'],
    evidence: [],
    modelVersion: 'rules-v2',
    createdAt: '2026-06-21T05:17:00Z',
  },
  aiDiagnosis: {
    id: 'ai_z9',
    summary: 'order-service 出现 CPU 高位、接口慢和健康检查失败。',
    rootCause: '疑似 CPU 饱和。',
    impact: '影响 order-service。',
    nextSteps: ['查看 CPU Top 进程'],
    evidenceRefs: ['evd_cpu'],
    status: 'completed',
    provider: 'aiops-agent',
    model: 'mock',
    agentName: 'aegis_diagnosis_graph',
    runbookSuggestions: [],
    risks: [],
    createdAt: '2026-06-21T05:18:00Z',
  },
  report: {
    id: 'rpt_z9',
    incidentId: 'inc_z9',
    versionNo: 1,
    markdownContent: '# 故障报告：order-service 主机与服务异常',
  },
}

const { mockGetIncidentBundle } = vi.hoisted(() => ({
  mockGetIncidentBundle: vi.fn(),
}))

vi.mock('../api/client', () => ({
  getIncidentBundle: mockGetIncidentBundle,
  collectIncidentEvidence: vi.fn().mockResolvedValue({ evidenceCreated: 3, message: 'ok' }),
  collectIncidentEvidenceByCollector: vi
    .fn()
    .mockResolvedValue({ evidenceCreated: 3, message: 'ok' }),
  analyzeIncidentRca: vi.fn().mockResolvedValue({
    id: 'rca_2',
    suspectedRootCause: 'CPU high',
  }),
  runIncidentAiDiagnosis: vi.fn().mockResolvedValue({
    id: 'ai_2',
    summary: 'AI diagnosis result',
  }),
  generateReport: vi.fn().mockResolvedValue({
    id: 'rpt_2',
    markdownContent: '# Report v2',
  }),
}))

afterEach(() => {
  mockGetIncidentBundle.mockClear()
})

describe('Phase Z9 console smoke', () => {
  beforeEach(() => {
    mockGetIncidentBundle.mockResolvedValue(mockIncidentBundle)
  })

  it('walks incident detail tabs and actions', async () => {
    const api = await import('../api/client')

    renderWithRouter(
      <Routes>
        <Route path="/incidents/:incidentId" element={<IncidentDetailPage />} />
      </Routes>,
      ['/incidents/inc_z9'],
    )

    expect(
      await screen.findAllByText('order-service 主机与服务异常', { selector: 'h2' }),
    ).toHaveLength(1)

    await userEvent.click(screen.getByRole('tab', { name: 'Evidence' }))
    expect(screen.getByText('CPU 使用率持续高位')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'RCA' }))
    expect(screen.getByText('疑似 CPU 饱和导致服务响应变慢')).toBeInTheDocument()
    expect(screen.getByText('CPU_API_HEALTH_COMBINED')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'AI Diagnosis' }))
    expect(screen.getByText('查看 CPU Top 进程')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'Report' }))
    expect(await screen.findByText('故障报告：order-service 主机与服务异常')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Collect Evidence' }))
    await waitFor(() =>
      expect(api.collectIncidentEvidenceByCollector).toHaveBeenCalledWith('inc_z9'),
    )

    await userEvent.click(screen.getByRole('button', { name: 'Run RCA' }))
    await waitFor(() => expect(api.analyzeIncidentRca).toHaveBeenCalledWith('inc_z9', true))

    await userEvent.click(screen.getByRole('button', { name: 'Run AI Diagnosis' }))
    await waitFor(() => expect(api.runIncidentAiDiagnosis).toHaveBeenCalledWith('inc_z9'))

    await userEvent.click(screen.getByRole('button', { name: 'Generate Report' }))
    await waitFor(() => expect(api.generateReport).toHaveBeenCalledWith('inc_z9'))
  })
})

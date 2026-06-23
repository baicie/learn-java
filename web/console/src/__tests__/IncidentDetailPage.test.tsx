import '@testing-library/jest-dom/vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { IncidentDetailPage } from '../pages/IncidentDetailPage'
import { renderWithRouter } from '../test/test-utils'

const mockIncidents = [
  {
    incident: {
      id: 'inc_1',
      title: 'CPU-Alert-001',
      summary: 'CPU high',
      severity: 'critical',
      status: 'open',
      alertCount: 3,
      aggregationKey: 'key1',
      source: 'zabbix',
      primaryAssetId: 'asset_1',
      startedAt: '2026-06-21T05:10:00Z',
      detectedAt: '2026-06-21T05:12:00Z',
      lastSeenAt: '2026-06-21T05:15:00Z',
      resolvedAt: undefined,
      createdAt: '2026-06-21T05:12:00Z',
      updatedAt: '2026-06-21T05:12:00Z',
    },
    alerts: [],
    evidence: [],
    rca: null,
    aiDiagnosis: null,
    report: null,
  },
  {
    incident: {
      id: 'inc_2',
      title: 'Memory-Warning-002',
      summary: 'Memory elevated',
      severity: 'warning',
      status: 'investigating',
      alertCount: 1,
      aggregationKey: 'key2',
      source: 'zabbix',
      primaryAssetId: 'asset_2',
      startedAt: '2026-06-21T05:20:00Z',
      detectedAt: '2026-06-21T05:22:00Z',
      lastSeenAt: '2026-06-21T05:25:00Z',
      resolvedAt: undefined,
      createdAt: '2026-06-21T05:22:00Z',
      updatedAt: '2026-06-21T05:22:00Z',
    },
    alerts: [],
    evidence: [],
    rca: null,
    aiDiagnosis: null,
    report: null,
  },
]

const { mockGetIncidentBundle } = vi.hoisted(() => {
  let callIndex = 0
  return {
    mockGetIncidentBundle: vi.fn(() => mockIncidents[callIndex++]),
  }
})

vi.mock('../api/client', () => ({
  getIncidentBundle: mockGetIncidentBundle,
  collectIncidentEvidence: vi.fn().mockResolvedValue({ collected: 3, message: 'ok' }),
  analyzeIncidentRca: vi.fn().mockResolvedValue({
    id: 'rca_2',
    incidentId: 'inc_1',
    status: 'completed',
    suspectedRootCause: 'CPU high',
    confidence: 0.88,
    summary: 'RCA result',
    evidence: [],
    matchedRules: ['CPU_API_HEALTH_COMBINED'],
    evidenceRefs: ['evd_cpu'],
    modelVersion: 'rules-v2-evidence',
    createdAt: '2026-06-21T05:17:00Z',
  }),
  runIncidentAiDiagnosis: vi.fn().mockResolvedValue({
    id: 'ai_2',
    incidentId: 'inc_1',
    status: 'completed',
    provider: 'aiops-agent',
    model: 'mock',
    agentName: 'aegis_diagnosis_graph',
    summary: 'AI diagnosis result',
    rootCause: 'CPU high',
    impact: 'service degraded',
    nextSteps: [],
    runbookSuggestions: [],
    risks: [],
    createdAt: '2026-06-21T05:18:00Z',
  }),
  generateReport: vi.fn().mockResolvedValue({
    id: 'rpt_2',
    incidentId: 'inc_1',
    versionNo: 2,
    markdownContent: '# Report v2',
    createdAt: '2026-06-21T05:19:00Z',
  }),
}))

describe('IncidentDetailPage', () => {
  it('renders critical incident title and severity', async () => {
    renderWithRouter(
      <Routes>
        <Route path="/incidents/:incidentId" element={<IncidentDetailPage />} />
      </Routes>,
      ['/incidents/inc_1'],
    )
    expect(await screen.findByRole('heading', { level: 2 })).toHaveTextContent('CPU-Alert-001')
    expect(screen.getAllByText('critical')).toHaveLength(2)
  })

  it('shows all four action buttons after loading', async () => {
    renderWithRouter(
      <Routes>
        <Route path="/incidents/:incidentId" element={<IncidentDetailPage />} />
      </Routes>,
      ['/incidents/inc_2'],
    )
    expect(await screen.findByRole('heading', { level: 2 })).toHaveTextContent('Memory-Warning-002')
    expect(screen.getByRole('button', { name: 'Collect Evidence' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Run RCA' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Run AI Diagnosis' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generate Report' })).toBeInTheDocument()
  })
})

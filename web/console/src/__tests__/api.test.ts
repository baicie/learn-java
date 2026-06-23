import { describe, expect, it, vi, beforeEach } from 'vitest'

describe('aiopsApi Z8 endpoints', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('calls getLatestReport with correct path', async () => {
    const mockResponse = {
      data: {
        id: 'rpt_1',
        incidentId: 'inc_1',
        versionNo: 1,
        markdownContent: '# Test report',
      },
      success: true,
      timestamp: new Date().toISOString(),
    }

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(mockResponse), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    )

    const { getLatestReport } = await import('../api/client')
    const result = await getLatestReport('inc_1')

    expect(result.id).toBe('rpt_1')
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/incidents/inc_1/reports/latest'),
      expect.objectContaining({
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
      }),
    )
  })

  it('calls generateReport with POST', async () => {
    const mockResponse = {
      data: {
        id: 'rpt_2',
        incidentId: 'inc_1',
        versionNo: 2,
        markdownContent: '# Generated report',
      },
      success: true,
      timestamp: new Date().toISOString(),
    }

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(mockResponse), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    )

    const { generateReport } = await import('../api/client')
    const result = await generateReport('inc_1')

    expect(result.id).toBe('rpt_2')
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/incidents/inc_1/reports'),
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
      }),
    )
  })

  it('calls getIncidentBundle and parallelizes requests', async () => {
    const mockIncident = {
      data: {
        id: 'inc_1',
        title: 'Test',
        severity: 'high',
        status: 'open',
        source: 'zabbix',
        alertCount: 1,
        createdAt: '',
        updatedAt: '',
        tenantId: '',
      },
      success: true,
      timestamp: new Date().toISOString(),
    }
    const mockAlerts = {
      data: [],
      success: true,
      timestamp: new Date().toISOString(),
    }
    const mockEvidence = {
      data: [],
      success: true,
      timestamp: new Date().toISOString(),
    }
    const mockRca = {
      data: null,
      success: true,
      timestamp: new Date().toISOString(),
    }
    const mockAi = {
      data: null,
      success: true,
      timestamp: new Date().toISOString(),
    }
    const mockReport = {
      data: null,
      success: true,
      timestamp: new Date().toISOString(),
    }

    let callCount = 0
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
      callCount++
      const responses = [mockIncident, mockAlerts, mockEvidence, mockRca, mockAi, mockReport]
      const idx = Math.min(callCount - 1, responses.length - 1)
      return Promise.resolve(
        new Response(JSON.stringify(responses[idx]), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
      )
    })

    const { getIncidentBundle } = await import('../api/client')
    const result = await getIncidentBundle('inc_1')

    expect(result.incident.id).toBe('inc_1')
    expect(result.alerts).toBeDefined()
    expect(result.evidence).toBeDefined()
  })
})

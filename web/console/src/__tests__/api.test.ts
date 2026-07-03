import { describe, expect, it, vi, beforeEach } from 'vitest'

describe('aiopsApi Phase 1 endpoints', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('calls workbenchSummary with correct path', async () => {
    const mockResponse = {
      data: {
        activeIncidents: 2,
        criticalAlerts: 3,
        todayNewAlerts: 5,
        datasourceErrors: 0,
        pendingTasks: 1,
        moduleHealth: 'HEALTHY',
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

    const { workbenchSummary } = await import('../api/client')
    const result = await workbenchSummary()

    expect(result.moduleHealth).toBe('HEALTHY')
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/workbench/summary'),
      expect.any(Object),
    )
  })

  it('calls listPlatformModules with correct path', async () => {
    const mockResponse = {
      data: [
        {
          id: 'mod-1',
          moduleId: 'platform',
          name: '平台底座',
          version: '1.0.0',
          enabled: true,
          healthStatus: 'HEALTHY',
          configJson: '{}',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
      success: true,
      timestamp: new Date().toISOString(),
    }

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(mockResponse), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    )

    const { listPlatformModules } = await import('../api/client')
    const result = await listPlatformModules()

    expect(result).toHaveLength(1)
    expect(result[0].moduleId).toBe('platform')
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/api/modules'), expect.any(Object))
  })

  it('calls listPlatformUsers with correct path', async () => {
    const mockResponse = {
      data: [
        {
          id: 'user-1',
          tenantId: 'tenant-1',
          username: 'admin',
          displayName: 'Administrator',
          email: 'admin@local',
          status: 'active',
          roles: ['admin'],
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
      ],
      success: true,
      timestamp: new Date().toISOString(),
    }

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(mockResponse), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    )

    const { listPlatformUsers } = await import('../api/client')
    const result = await listPlatformUsers()

    expect(result).toHaveLength(1)
    expect(result[0].username).toBe('admin')
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/api/users'), expect.any(Object))
  })
})

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

  it('calls getIncidentBundle and unwraps IncidentDetailRecord', async () => {
    const mockIncidentDetail = {
      data: {
        incident: {
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
        alerts: [
          {
            id: 'alert_1',
            source: 'zabbix',
            severity: 'high',
            title: 'CPU High',
            status: 'open',
            fingerprint: 'fp1',
            startsAt: '',
            relationType: 'primary',
          },
        ],
        timeline: [],
      },
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
      const responses = [mockIncidentDetail, mockEvidence, mockRca, mockAi, mockReport]
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
    expect(result.incident.title).toBe('Test')
    expect(result.alerts).toHaveLength(1)
    expect(result.timeline).toEqual([])
    expect(result.evidence).toEqual([])
  })
})

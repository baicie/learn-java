import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { useAuthStore } from '@/stores/auth-store'
import { useAlerts, useIncidents } from '@/hooks/operations/use-operations'
import { Dashboard } from './index'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: React.ReactNode }) => (
    <a href='/incidents/inc-1'>{children}</a>
  ),
}))

vi.mock('@/api/calendars', () => ({
  listCalendars: vi.fn(async () => []),
}))

vi.mock('@/api/work-records/list', () => ({
  fetchRecordList: vi.fn(async () => ({ items: [], total: 0 })),
  fetchWorkdaySummary: vi.fn(async () => ({
    calendarName: '默认日历',
    workdayCount: 22,
  })),
}))

vi.mock('@/api/work-records/templates', () => ({
  listTemplates: vi.fn(async () => []),
}))

vi.mock('@/hooks/operations/use-operations', () => ({
  useAlerts: vi.fn(() => ({
    data: [
      {
        id: 'alt-1',
        tenantId: 'default',
        source: 'zabbix',
        severity: 'critical',
        title: 'CPU usage is high',
        status: 'open',
        startsAt: '2026-08-03T05:30:00Z',
        createdAt: '2026-08-03T05:30:00Z',
      },
    ],
    isError: false,
    isLoading: false,
    refetch: vi.fn(),
  })),
  useIncidents: vi.fn(() => ({
    data: [
      {
        id: 'inc-1',
        tenantId: 'default',
        title: 'Zabbix CPU saturation',
        summary: 'CPU alerts were correlated.',
        severity: 'critical',
        status: 'open',
        source: 'zabbix',
        primaryAssetId: 'asset-1',
        aggregationKey: 'zabbix:host-1',
        alertCount: 4,
        startedAt: '2026-08-03T05:30:00Z',
        detectedAt: '2026-08-03T05:30:00Z',
        lastSeenAt: '2026-08-03T05:36:00Z',
        resolvedAt: null,
        createdAt: '2026-08-03T05:30:00Z',
        updatedAt: '2026-08-03T05:36:00Z',
      },
    ],
    isError: false,
    isLoading: false,
    refetch: vi.fn(),
  })),
}))

describe('Dashboard AIOps summary', () => {
  beforeEach(() => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'admin',
      tenantId: 'default',
      username: 'admin',
      displayName: 'Admin',
      roles: [],
      permissions: ['alert:read', 'incident:read'],
      dataScopes: {},
    })
  })

  it('renders live alert and incident data for the current tenant', async () => {
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <Dashboard />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('AIOps 故障态势')).toBeVisible()
    await expect
      .element(screen.getByText('Zabbix CPU saturation'))
      .toBeVisible()
    await expect.element(screen.getByText('CPU usage is high')).toBeVisible()
    await expect.element(screen.getByText('开放告警')).toBeVisible()
    await expect.element(screen.getByText('活动 Incident')).toBeVisible()
    await expect.element(screen.getByText('最近共 1 个 Incident')).toBeVisible()
  })

  it('uses skeletons while AIOps lists are loading', async () => {
    vi.mocked(useAlerts).mockReturnValue({
      data: undefined,
      isError: false,
      isLoading: true,
      refetch: vi.fn(),
    } as never)
    vi.mocked(useIncidents).mockReturnValue({
      data: undefined,
      isError: false,
      isLoading: true,
      refetch: vi.fn(),
    } as never)

    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <Dashboard />
      </QueryClientProvider>
    )

    const loadingLists = screen.container.querySelectorAll(
      '[aria-label="AIOps 数据加载中"]'
    )
    expect(loadingLists).toHaveLength(2)
    expect(
      loadingLists[0]?.querySelectorAll('[data-slot="skeleton"]')
    ).toHaveLength(3)
  })

  it('uses shared empty states when the tenant has no AIOps data', async () => {
    vi.mocked(useAlerts).mockReturnValue({
      data: [],
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    } as never)
    vi.mocked(useIncidents).mockReturnValue({
      data: [],
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    } as never)

    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <Dashboard />
      </QueryClientProvider>
    )

    await expect
      .element(screen.getByText('暂无 Incident', { exact: true }))
      .toBeVisible()
    await expect
      .element(screen.getByText('暂无告警', { exact: true }))
      .toBeVisible()
    expect(
      screen.container.querySelectorAll('[data-slot="empty-state"]')
    ).toHaveLength(2)
  })
})

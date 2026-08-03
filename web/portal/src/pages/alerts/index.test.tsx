import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import type { AlertEvent } from '@/lib/operations/operations'
import { useAlerts } from '@/hooks/operations/use-operations'
import { AlertsPage } from './index'

const alerts: AlertEvent[] = [
  {
    id: 'alert-1',
    tenantId: 'default',
    source: 'zabbix',
    severity: 'critical',
    title: 'CPU usage is high',
    status: 'open',
    startsAt: '2026-08-03T05:30:00Z',
    createdAt: '2026-08-03T05:30:00Z',
  },
]

vi.mock('@/hooks/operations/use-operations', () => ({
  useAlerts: vi.fn(),
}))

vi.mock('@/components/layout/header', () => ({
  Header: ({ children }: { children: React.ReactNode }) => (
    <header>{children}</header>
  ),
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

describe('AlertsPage', () => {
  it('preserves a deep-linked page while alerts are loading', async () => {
    vi.mocked(useAlerts).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      refetch: vi.fn(),
    } as never)
    const onSearch = vi.fn()

    await render(
      <AlertsPage
        search={{
          page: 3,
          pageSize: 20,
          keyword: '',
          statuses: [],
          severities: [],
        }}
        onSearch={onSearch}
      />
    )

    expect(onSearch).not.toHaveBeenCalled()
  })

  it('labels totals as a filtered view of the recent API window', async () => {
    vi.mocked(useAlerts).mockReturnValue({
      data: alerts,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never)

    const screen = await render(
      <AlertsPage
        search={{
          page: 1,
          pageSize: 20,
          keyword: '',
          statuses: [],
          severities: [],
        }}
        onSearch={vi.fn()}
      />
    )

    await expect
      .element(screen.getByText('最近 100 条中匹配 1 条'))
      .toBeVisible()
  })
})

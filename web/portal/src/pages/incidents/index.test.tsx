import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import type { Incident } from '@/lib/operations/operations'
import { useIncidents } from '@/hooks/operations/use-operations'
import { IncidentsPage } from './index'

const incidents: Incident[] = [
  {
    id: 'inc-1',
    tenantId: 'tenant-1',
    title: 'Checkout API unavailable',
    summary: 'Requests are failing at the edge.',
    severity: 'critical',
    status: 'open',
    source: 'zabbix',
    primaryAssetId: 'asset-1',
    aggregationKey: 'checkout-api',
    alertCount: 4,
    startedAt: '2026-08-03T05:30:00Z',
    detectedAt: '2026-08-03T05:30:00Z',
    lastSeenAt: '2026-08-03T05:36:00Z',
    resolvedAt: null,
    createdAt: '2026-08-03T05:30:00Z',
    updatedAt: '2026-08-03T05:36:00Z',
  },
]

vi.mock('@/hooks/operations/use-operations', () => ({
  useIncidents: vi.fn(),
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: React.ReactNode }) => (
    <a href='/incidents/inc-1'>{children}</a>
  ),
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

describe('IncidentsPage', () => {
  it('renders incident data and submits a keyword filter through URL state', async () => {
    vi.mocked(useIncidents).mockReturnValue({
      data: incidents,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never)
    const onSearch = vi.fn()
    const screen = await render(
      <IncidentsPage
        search={{
          page: 1,
          pageSize: 20,
          keyword: '',
          status: '',
          severity: '',
          source: '',
        }}
        onSearch={onSearch}
      />
    )

    await expect
      .element(screen.getByText('Checkout API unavailable'))
      .toBeVisible()
    await expect.element(screen.getByText('4 条告警')).toBeVisible()
    await expect.element(screen.getByText('最近 Incident')).toBeVisible()

    const input = screen.getByRole('textbox', { name: '搜索 Incident' })
    await input.fill('checkout')
    input
      .element()
      .dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true })
      )

    expect(onSearch).toHaveBeenCalledWith({ keyword: 'checkout', page: 1 })
  })

  it('resynchronizes the keyword input when router search changes', async () => {
    vi.mocked(useIncidents).mockReturnValue({
      data: incidents,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never)
    const onSearch = vi.fn()
    const initialSearch = {
      page: 1,
      pageSize: 20,
      keyword: 'checkout',
      status: '',
      severity: '',
      source: '',
    }
    const screen = await render(
      <IncidentsPage search={initialSearch} onSearch={onSearch} />
    )
    const input = screen.getByRole('textbox', { name: '搜索 Incident' })
    await input.fill('local draft')

    await screen.rerender(
      <IncidentsPage
        search={{ ...initialSearch, keyword: 'database' }}
        onSearch={onSearch}
      />
    )

    expect((input.element() as HTMLInputElement).value).toBe('database')
  })

  it('preserves a deep-linked page while incidents are loading', async () => {
    vi.mocked(useIncidents).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      refetch: vi.fn(),
    } as never)
    const onSearch = vi.fn()

    await render(
      <IncidentsPage
        search={{
          page: 3,
          pageSize: 20,
          keyword: '',
          status: '',
          severity: '',
          source: '',
        }}
        onSearch={onSearch}
      />
    )

    expect(onSearch).not.toHaveBeenCalled()
  })

  it('offers disaster as a severity filter', async () => {
    vi.mocked(useIncidents).mockReturnValue({
      data: incidents,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never)
    const screen = await render(
      <IncidentsPage
        search={{
          page: 1,
          pageSize: 20,
          keyword: '',
          status: '',
          severity: '',
          source: '',
        }}
        onSearch={vi.fn()}
      />
    )

    await screen.getByRole('combobox').nth(1).click()
    await expect
      .element(screen.getByRole('option', { name: '灾难' }))
      .toBeVisible()
  })
})

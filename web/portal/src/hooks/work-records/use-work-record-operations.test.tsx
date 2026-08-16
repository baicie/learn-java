import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import { useAuthStore } from '@/stores/auth-store'
import { useWorkRecordOperations } from './use-work-record-operations'

const api = vi.hoisted(() => ({
  getStatistics: vi.fn(async () => ({})),
  getWorkload: vi.fn(async () => ({})),
  listHandovers: vi.fn(async () => []),
  listMonthlyAiGenerations: vi.fn(async () => []),
  listWeeklyAiGenerations: vi.fn(async () => []),
  listPendingApprovalTasks: vi.fn(async () => []),
  listMarketPackages: vi.fn(async () => []),
}))

vi.mock('@/api/work-records/extensions', () => api)

function authorize(tenantId: string, dataScope: 'SELF' | 'ALL' = 'ALL') {
  useAuthStore.getState().auth.setPrincipal({
    userId: 'reviewer-1',
    tenantId,
    username: 'reviewer',
    displayName: 'Reviewer',
    roles: [],
    permissions: ['work-record:ai:review', 'work-record:read:all'],
    dataScopes: { 'work-record': dataScope },
  })
}

describe('useWorkRecordOperations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('partitions period-report queries by tenant and allows reviewers to load them', async () => {
    const client = new QueryClient()
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    )
    authorize('tenant-1')

    const first = await renderHook(
      () =>
        useWorkRecordOperations(
          '2026-07-15T00:00:00+08:00',
          '2026-07-31T23:59:59+08:00'
        ),
      { wrapper }
    )
    await expect
      .poll(() => api.listWeeklyAiGenerations.mock.calls.length)
      .toBe(1)
    expect(first.result.current.canGeneratePeriodReports).toBe(false)

    authorize('tenant-2')
    first.rerender()
    await expect
      .poll(() => api.listWeeklyAiGenerations.mock.calls.length)
      .toBe(2)

    const keys = client
      .getQueryCache()
      .getAll()
      .map((query) => query.queryKey)
    expect(keys).toContainEqual([
      'work-record-weekly-ai',
      'tenant-1',
      '2026-07-13',
    ])
    expect(keys).toContainEqual([
      'work-record-weekly-ai',
      'tenant-2',
      '2026-07-13',
    ])
  })

  it('does not load period reports when read:all is paired with SELF scope', async () => {
    const client = new QueryClient()
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    )
    authorize('tenant-1', 'SELF')

    const result = await renderHook(
      () =>
        useWorkRecordOperations(
          '2026-07-15T00:00:00+08:00',
          '2026-07-31T23:59:59+08:00'
        ),
      { wrapper }
    )

    await expect.poll(() => api.listMarketPackages.mock.calls.length).toBe(1)
    expect(api.listWeeklyAiGenerations).not.toHaveBeenCalled()
    expect(api.listMonthlyAiGenerations).not.toHaveBeenCalled()
    expect(result.result.current.canReadPeriodReports).toBe(false)
    expect(result.result.current.canGeneratePeriodReports).toBe(false)
  })
})

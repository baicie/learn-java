import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { AuthorizationPrincipal } from '@/auth/authorization-types'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { useAuthStore } from '@/stores/auth-store'
import { WorkRecordOperationsPage } from './operations'

const api = vi.hoisted(() => ({
  actOnApprovalTask: vi.fn(async () => ({ status: 'approved' })),
  requestMonthlyAiReport: vi.fn(async () => ({})),
}))

vi.mock('@/api/work-records/extensions', () => api)
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(async () => undefined),
  useSearch: () => ({
    from: '2026-07-01T00:00:00.000Z',
    to: '2026-07-31T23:59:59.000Z',
  }),
}))
vi.mock('@/hooks/work-records/use-work-record-operations', () => ({
  useWorkRecordOperations: () => ({
    statistics: {
      data: { totalRecords: 12, completedRecords: 9, distinctOwners: 3 },
      error: null,
      isLoading: false,
    },
    workload: {
      data: {
        workdayCount: 23,
        users: [
          {
            userId: 'user-1',
            displayName: 'Alice',
            recordCount: 12,
            completedCount: 9,
            numericWorkload: 30,
            recordsPerWorkday: 0.5,
          },
        ],
      },
      error: null,
      isLoading: false,
    },
    handovers: {
      data: [
        {
          id: 'handover-1',
          fromUserId: 'Alice',
          toUserId: 'Bob',
          status: 'submitted',
          summary: '夜班交接',
        },
      ],
    },
    market: {
      data: [{ id: 'market-1', name: '标准日报', versionNo: 2 }],
    },
    monthlyReports: {
      data: [
        {
          id: 'ai-1',
          resourceId: '2026-07',
          status: 'success',
          outputMarkdown: '## 七月月报',
        },
      ],
    },
    approvals: {
      data: [
        {
          id: 'task-1',
          recordTitle: '生产日报',
          dueAt: '2026-07-15T00:00:00Z',
        },
      ],
    },
  }),
}))

function authorize() {
  const principal: AuthorizationPrincipal = {
    userId: 'user-1',
    tenantId: 'tenant-1',
    username: 'alice',
    displayName: 'Alice',
    roles: ['record_admin'],
    permissions: [
      'work-record:analytics',
      'work-record:handover',
      'work-record:ai:generate',
      'work-record:approval:act',
    ],
    dataScopes: {},
  }
  useAuthStore.getState().auth.setPrincipal(principal)
}

describe('WorkRecordOperationsPage', () => {
  beforeEach(() => {
    authorize()
    vi.clearAllMocks()
  })

  it('renders analytics, handover, market, monthly report and approval actions', async () => {
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <WorkRecordOperationsPage />
      </QueryClientProvider>
    )

    await expect.element(screen.getByText('工作记录运营中心')).toBeVisible()
    await expect
      .element(screen.getByRole('cell', { name: 'Alice' }))
      .toBeVisible()
    await expect.element(screen.getByText('夜班交接')).toBeVisible()
    await expect.element(screen.getByText('标准日报')).toBeVisible()
    await expect.element(screen.getByText('## 七月月报')).toBeVisible()
    await expect.element(screen.getByText('生产日报')).toBeVisible()

    await screen.getByRole('button', { name: '生成月报' }).click()
    expect(api.requestMonthlyAiReport).toHaveBeenCalledWith(
      '2026-07-01T00:00:00.000Z'
    )
    await screen.getByRole('button', { name: '同意' }).click()
    expect(api.actOnApprovalTask).toHaveBeenCalledWith('task-1', true, '同意')
  })
})

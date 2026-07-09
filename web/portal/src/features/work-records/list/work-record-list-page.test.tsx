import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { WorkRecordListPage } from './work-record-list-page'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, ...props }: { children: React.ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  useNavigate: () => vi.fn(),
}))

vi.mock('./api', () => ({
  fetchRecordListMeta: async () => ({
    templates: [
      {
        id: 'tpl1',
        code: 'daily',
        name: '日报模板',
        status: 'published',
        enabled: true,
        currentVersionId: 'v1',
      },
    ],
    columns: [
      {
        key: 'title',
        title: '标题',
        source: 'builtin',
        fieldCode: null,
        fieldType: 'text',
        visibleByDefault: true,
        sortable: true,
        sortOrder: 1,
      },
      {
        key: 'custom.priority',
        title: '优先级',
        source: 'custom',
        fieldCode: 'priority',
        fieldType: 'select',
        visibleByDefault: true,
        sortable: false,
        sortOrder: 2,
      },
    ],
    filterFields: [
      {
        key: 'custom.priority',
        title: '优先级',
        source: 'custom',
        fieldCode: 'priority',
        fieldType: 'select',
        visibleByDefault: true,
        sortable: false,
        sortOrder: 2,
      },
    ],
    dictCodes: ['record_priority'],
    maxExportRows: 5000,
    quickViews: ['mine', 'all', 'today', 'this_week', 'this_month'],
  }),
  fetchRecordList: async () => ({
    total: 1,
    page: 1,
    size: 20,
    items: [
      {
        id: 'r1',
        tenantId: 't1',
        templateId: 'tpl1',
        templateVersionId: 'v1',
        title: '日报',
        status: 'done',
        ownerId: 'u1',
        creatorId: 'u1',
        recordTime: '2026-01-01T00:00:00Z',
        builtinDataJson: '{}',
        customDataJson: '{"priority":"P1"}',
        rowVersion: 1,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
        deletedAt: null,
      },
    ],
  }),
}))

describe('WorkRecordListPage', () => {
  it('renders enterprise list page', async () => {
    const screen = await render(
      <QueryClientProvider client={new QueryClient()}>
        <WorkRecordListPage />
      </QueryClientProvider>,
    )

    await expect.element(screen.getByText('工作记录')).toBeVisible()
    await expect.element(screen.getByRole('cell', { name: '日报' })).toBeVisible()
    await expect.element(screen.getByText('P1')).toBeVisible()
    await expect.element(screen.getByText('我的记录')).toBeVisible()

    expect(screen.getByText('动态字段筛选', { exact: true }).element()).toBeTruthy()
    expect(screen.getByText('列显示控制', { exact: true }).element()).toBeTruthy()
  })
})
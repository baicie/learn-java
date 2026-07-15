import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { DEFAULT_LANGUAGE, i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { useAuthStore } from '@/stores/auth-store'
import { ConfirmProvider } from '@/components/feedback/confirm-provider'
import { WorkRecordListPage } from './work-record-list-page'

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    children,
    ...props
  }: {
    children: React.ReactNode
  } & React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a {...props}>{children}</a>
  ),
  useNavigate: () => vi.fn(async () => undefined),
}))

vi.mock('@/api/dictionaries', () => ({
  listDictItems: async () => [],
}))

vi.mock('@/api/work-records/list', () => {
  return {
    fetchRecordListMeta: vi.fn(async () => ({
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
          optionSource: null,
          dictCode: null,
          optionsJson: '[]',
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
          optionSource: 'dict',
          dictCode: 'record_priority',
          optionsJson: '[]',
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
          optionSource: 'dict',
          dictCode: 'record_priority',
          optionsJson: '[]',
          visibleByDefault: true,
          sortable: false,
          sortOrder: 2,
        },
      ],
      dictCodes: ['record_priority'],
      maxExportRows: 5000,
      quickViews: [
        'mine',
        'all',
        'today',
        'this_week',
        'this_month',
        'this_work_month',
        'recent_workdays',
      ],
    })),
    fetchRecordList: vi.fn(async () => ({
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
    })),
    fetchWorkdaySummary: vi.fn(async () => ({
      calendarId: 'cal1',
      calendarName: '中国大陆 2026 工作日历',
      timeZone: 'Asia/Shanghai',
      month: '2026-07',
      periodStart: '2026-07-01',
      periodEnd: '2026-07-31',
      workdayCount: 23,
      firstWorkday: '2026-07-01',
      lastWorkday: '2026-07-31',
    })),
    fetchRecordUserNames: vi.fn(async () => ({ u1: '张三' })),
  }
})

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <ConfirmProvider>
          <WorkRecordListPage />
        </ConfirmProvider>
      </I18nextProvider>
    </QueryClientProvider>
  )
}

describe('WorkRecordListPage', () => {
  beforeEach(() => {
    void i18n.changeLanguage(DEFAULT_LANGUAGE)
    useAuthStore.getState().auth.setPrincipal({
      userId: 'u1',
      tenantId: 't1',
      username: 'admin',
      displayName: 'Admin',
      roles: ['admin'],
      permissions: [
        'work-record:read:all',
        'work-record:write',
        'work-record:import',
        'work-record:export',
      ],
      dataScopes: {},
    })
  })

  it('renders enterprise list page', async () => {
    const screen = await renderPage()

    await expect
      .element(screen.getByRole('heading', { level: 1, name: '记录列表' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('cell', { name: '日报' }))
      .toBeVisible()

    expect(screen.getByText('动态筛选', { exact: true }).element()).toBeTruthy()
    expect(
      screen.getByText('列显示控制', { exact: true }).element()
    ).toBeTruthy()
    await expect
      .element(screen.getByRole('button', { name: '导入' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '导出' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('combobox', { name: '每页条数' }))
      .toBeVisible()
    await expect
      .element(screen.getByText('按模板配置的动态字段精确筛选记录。'))
      .toBeVisible()
  })

  it('keeps prior data when a refresh fails', async () => {
    const api = await import('@/api/work-records/list')
    // Initial fetch returns data
    ;(
      api.fetchRecordList as unknown as ReturnType<typeof vi.fn>
    ).mockResolvedValueOnce({
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
    })

    const screen = await renderPage()

    await expect
      .element(screen.getByRole('cell', { name: '日报' }))
      .toBeVisible()

    // Subsequent fetches reject and never recover — placeholderData keeps
    // prior rows visible instead of falling back to the blocking ErrorState.
    ;(
      api.fetchRecordList as unknown as ReturnType<typeof vi.fn>
    ).mockRejectedValue(new Error('refresh failed'))

    const search = screen.getByPlaceholder('搜索记录标题…')
    await userEvent.fill(search, 'tester-refresh')

    // Old rows must still render and the blocking ErrorState must not appear.
    await expect
      .element(screen.getByRole('cell', { name: '日报' }))
      .toBeVisible()

    // Allow retries to settle and confirm the page does not show the
    // "加载失败" blocking error label.
    await new Promise((resolve) => setTimeout(resolve, 1500))

    await expect
      .element(screen.getByRole('cell', { name: '日报' }))
      .toBeVisible()

    await expect.element(screen.getByText('加载失败')).not.toBeInTheDocument()
  })

  it('hides mutation actions from read-only users', async () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'u2',
      tenantId: 't1',
      username: 'reader',
      displayName: '只读用户',
      roles: ['reader'],
      permissions: ['work-record:read:self'],
      dataScopes: {},
    })

    const screen = await renderPage()

    await expect
      .element(screen.getByRole('heading', { level: 1, name: '记录列表' }))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '导入' }))
      .not.toBeInTheDocument()
    await expect
      .element(screen.getByRole('button', { name: '导出' }))
      .not.toBeInTheDocument()
    await expect
      .element(screen.getByRole('button', { name: '新建记录' }))
      .not.toBeInTheDocument()
  })
})

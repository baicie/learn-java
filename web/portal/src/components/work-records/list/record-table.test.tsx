import type { ReactNode } from 'react'
import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { useAuthStore } from '@/stores/auth-store'
import { RecordTable } from './record-table'
import type { RecordListColumn, WorkRecord } from './types'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: ReactNode }) => <a href='#'>{children}</a>,
}))

const record: WorkRecord = {
  id: 'record-1',
  tenantId: 'tenant-1',
  templateId: 'template-1',
  templateVersionId: 'version-1',
  title: '日报',
  status: 'done',
  ownerId: 'user-1',
  creatorId: 'user-1',
  recordTime: '2026-07-11T10:00:00+08:00',
  builtinDataJson: '{}',
  customDataJson: '{"priority":"P2","oldField":"历史值"}',
  rowVersion: 1,
  createdAt: '2026-07-11T10:00:00+08:00',
  updatedAt: '2026-07-11T10:00:00+08:00',
  deletedAt: null,
}

function column(fieldCode: string, title: string): RecordListColumn {
  return {
    key: `custom.${fieldCode}`,
    title,
    source: 'custom',
    fieldCode,
    fieldType: 'text',
    optionSource: fieldCode === 'priority' ? 'dict' : 'static',
    dictCode: fieldCode === 'priority' ? 'priority' : null,
    optionsJson: '[]',
    visibleByDefault: true,
    sortable: true,
    exportable: true,
    sortOrder: 0,
  }
}

function renderTable(
  columns: RecordListColumn[],
  onSort = vi.fn(),
  records: WorkRecord[] = [record],
  selection?: {
    selectedIds?: ReadonlySet<string>
    onToggleRecord?: (id: string) => void
    onToggleAll?: (checked: boolean) => void
  }
) {
  return render(
    <I18nextProvider i18n={i18n} defaultNS='translation'>
      <RecordTable
        records={records}
        columns={columns}
        dictOptions={{
          priority: [
            {
              value: 'P2',
              label: '中',
              enabled: false,
            },
          ],
        }}
        sortBy='recordTime'
        sortDir='desc'
        onSort={onSort}
        templateNames={{ 'template-1': '日报模板' }}
        userNames={{ 'user-1': '张三' }}
        selectedIds={selection?.selectedIds ?? new Set()}
        onToggleRecord={selection?.onToggleRecord ?? vi.fn()}
        onToggleAll={selection?.onToggleAll ?? vi.fn()}
      />
    </I18nextProvider>
  )
}

describe('RecordTable', () => {
  it('renders an explicit record ID header and value', async () => {
    const screen = await renderTable([
      {
        key: 'id',
        title: '记录 ID',
        source: 'builtin',
        fieldCode: null,
        fieldType: 'text',
        optionSource: null,
        dictCode: null,
        optionsJson: '[]',
        visibleByDefault: true,
        sortable: false,
        exportable: true,
        sortOrder: 0,
      },
    ])

    await expect.element(screen.getByText('记录 ID')).toBeVisible()
    await expect.element(screen.getByText('record-1')).toBeVisible()
    await expect.element(screen.getByText('操作')).toBeVisible()
  })

  it('uses the shared empty state when no records exist', async () => {
    const screen = await render(
      <I18nextProvider i18n={i18n} defaultNS='translation'>
        <RecordTable
          records={[]}
          columns={[]}
          sortBy='recordTime'
          sortDir='desc'
          onSort={vi.fn()}
          selectedIds={new Set()}
          onToggleRecord={vi.fn()}
          onToggleAll={vi.fn()}
        />
      </I18nextProvider>
    )

    await expect.element(screen.getByText('暂无记录')).toBeVisible()
    expect(document.querySelector('[data-slot="empty-state"]')).not.toBeNull()
  })

  it('renders disabled dictionary label', async () => {
    const screen = await renderTable([column('priority', '优先级')])

    await expect.element(screen.getByText('中（已禁用）')).toBeVisible()
  })

  it('renders historical value after field is removed from current template', async () => {
    const screen = await renderTable([column('oldField', '旧字段')])

    await expect.element(screen.getByText('历史值')).toBeVisible()
  })

  it('emits dynamic column sort', async () => {
    const onSort = vi.fn()

    const screen = await renderTable([column('priority', '优先级')], onSort)

    await screen.getByRole('button', { name: '优先级' }).click()

    expect(onSort).toHaveBeenCalledWith('custom.priority', 'desc')
  })

  it('renders template name instead of internal id', async () => {
    const screen = await renderTable([
      {
        ...column('unused', '模板'),
        key: 'templateId',
        source: 'builtin',
        fieldCode: null,
      },
    ])

    await expect.element(screen.getByText('日报模板')).toBeVisible()
    await expect.element(screen.getByText('template-1')).not.toBeInTheDocument()
  })

  it('renders creator display name instead of internal id', async () => {
    const screen = await renderTable([
      {
        ...column('unused', '创建人'),
        key: 'creatorId',
        source: 'builtin',
        fieldCode: null,
      },
    ])

    await expect.element(screen.getByText('张三')).toBeVisible()
    await expect.element(screen.getByText('user-1')).not.toBeInTheDocument()
  })

  it('renders date columns with the shared slash format', async () => {
    const screen = await renderTable([
      {
        ...column('unused', '记录时间'),
        key: 'recordTime',
        source: 'builtin',
        fieldCode: null,
      },
    ])

    await expect.element(screen.getByText('2026/07/11 10:00:00')).toBeVisible()
  })

  it('shows an edit entry for drafts when the user can write records', async () => {
    useAuthStore.getState().auth.setPrincipal({
      userId: 'user-1',
      tenantId: 'tenant-1',
      username: 'user-1',
      displayName: '张三',
      roles: [],
      permissions: ['work-record:write'],
      dataScopes: {},
    })

    const screen = await renderTable([], vi.fn(), [
      { ...record, status: 'draft' },
    ])

    await expect
      .element(screen.getByRole('link', { name: '编辑' }))
      .toBeVisible()
  })

  it('emits toggle callback when a row checkbox is clicked', async () => {
    const onToggleRecord = vi.fn()
    const screen = await renderTable(
      [column('priority', '优先级')],
      vi.fn(),
      [record],
      { onToggleRecord }
    )

    await screen.getByRole('checkbox', { name: '选择该行' }).click()

    expect(onToggleRecord).toHaveBeenCalledWith('record-1')
  })

  it('emits select-all callback from the header checkbox', async () => {
    const onToggleAll = vi.fn()
    const screen = await renderTable(
      [column('priority', '优先级')],
      vi.fn(),
      [record],
      { onToggleAll }
    )

    await screen.getByRole('checkbox', { name: '全选当前页' }).click()

    expect(onToggleAll).toHaveBeenCalledWith(true)
  })

  it('marks the header checkbox as indeterminate when part of the page is selected', async () => {
    const other: WorkRecord = { ...record, id: 'record-2', title: '周报' }
    const screen = await renderTable(
      [column('priority', '优先级')],
      vi.fn(),
      [record, other],
      { selectedIds: new Set(['record-1']) }
    )

    const header = screen.getByRole('checkbox', { name: '全选当前页' })
    await expect.element(header).toHaveAttribute('data-state', 'indeterminate')
  })

  it('checks the header checkbox when the whole page is selected', async () => {
    const screen = await renderTable(
      [column('priority', '优先级')],
      vi.fn(),
      [record],
      { selectedIds: new Set(['record-1']) }
    )

    const header = screen.getByRole('checkbox', { name: '全选当前页' })
    await expect.element(header).toHaveAttribute('data-state', 'checked')
  })
})

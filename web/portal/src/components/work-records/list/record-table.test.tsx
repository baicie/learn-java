import type { ReactNode } from 'react'
import { i18n } from '@/i18n'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
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

function renderTable(columns: RecordListColumn[], onSort = vi.fn()) {
  return render(
    <I18nextProvider i18n={i18n} defaultNS='translation'>
      <RecordTable
        records={[record]}
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
      />
    </I18nextProvider>
  )
}

describe('RecordTable', () => {
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
})

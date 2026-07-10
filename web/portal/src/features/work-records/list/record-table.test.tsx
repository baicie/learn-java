import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { RecordTable } from './record-table'
import type { DictOptionMap, RecordListColumn, WorkRecord } from './types'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, ...props }: { children: React.ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}))

describe('RecordTable', () => {
  it('renders empty state', async () => {
    const screen = await render(
      <RecordTable
        records={[]}
        columns={[]}
        sortBy='recordTime'
        sortDir='desc'
        onSort={vi.fn()}
      />
    )

    await expect.element(screen.getByText('暂无记录')).toBeVisible()
  })

  it('renders builtin and dynamic columns', async () => {
    const screen = await render(
      <RecordTable
        records={[record()]}
        columns={[
          column('title', '标题', 'builtin', null),
          column('custom.priority', '优先级', 'custom', 'priority'),
        ]}
        sortBy='recordTime'
        sortDir='desc'
        onSort={vi.fn()}
      />
    )

    await expect.element(screen.getByText('日报')).toBeVisible()
    await expect.element(screen.getByText('P1')).toBeVisible()
  })

  it('renders dictionary labels and disabled suffix', async () => {
    const screen = await render(
      <RecordTable
        records={[recordWith('{"priority":"P2"}')]}
        columns={[
          column(
            'custom.priority',
            '优先级',
            'custom',
            'priority',
            'select',
            'record_priority'
          ),
        ]}
        dictOptions={
          {
            record_priority: [
              { value: 'P1', label: 'P1-紧急', enabled: true },
              { value: 'P2', label: 'P2-高', enabled: false },
            ],
          } satisfies DictOptionMap
        }
        sortBy='recordTime'
        sortDir='desc'
        onSort={vi.fn()}
      />
    )

    await expect.element(screen.getByText('P2-高（已禁用）')).toBeVisible()
  })

  it('renders multi-select values joined by 、', async () => {
    const screen = await render(
      <RecordTable
        records={[recordWith('{"tags":["a","b"]}')]}
        columns={[
          column('custom.tags', '标签', 'custom', 'tags', 'multi_select'),
        ]}
        sortBy='recordTime'
        sortDir='desc'
        onSort={vi.fn()}
      />
    )

    await expect.element(screen.getByText('a、b')).toBeVisible()
  })
})

function column(
  key: string,
  title: string,
  source: 'builtin' | 'custom',
  fieldCode: string | null,
  fieldType = 'text',
  dictCode: string | null = null,
  exportable = true
): RecordListColumn {
  return {
    key,
    title,
    source,
    fieldCode,
    fieldType,
    optionSource: dictCode ? 'dict' : 'static',
    dictCode,
    optionsJson: '[]',
    visibleByDefault: true,
    sortable: true,
    exportable,
    sortOrder: 1,
  }
}

function record(): WorkRecord {
  return recordWith('{"priority":"P1"}')
}

function recordWith(custom: string): WorkRecord {
  return {
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
    customDataJson: custom,
    rowVersion: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deletedAt: null,
  }
}

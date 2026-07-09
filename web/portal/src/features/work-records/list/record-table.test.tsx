import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { RecordTable } from './record-table'
import type { RecordListColumn, WorkRecord } from './types'

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
      />,
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
      />,
    )

    await expect.element(screen.getByText('日报')).toBeVisible()
    await expect.element(screen.getByText('P1')).toBeVisible()
  })
})

function column(
  key: string,
  title: string,
  source: 'builtin' | 'custom',
  fieldCode: string | null,
): RecordListColumn {
  return {
    key,
    title,
    source,
    fieldCode,
    fieldType: 'text',
    visibleByDefault: true,
    sortable: true,
    sortOrder: 1,
  }
}

function record(): WorkRecord {
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
    customDataJson: '{"priority":"P1"}',
    rowVersion: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    deletedAt: null,
  }
}
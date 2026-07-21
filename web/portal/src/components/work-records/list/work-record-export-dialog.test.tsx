import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { downloadExport, exportWorkRecords } from '@/api/work-records/export'
import type { ListQueryState, RecordListColumn, RecordListMeta } from './types'
import { WorkRecordExportDialog } from './work-record-export-dialog'

vi.mock('@/api/work-records/export', () => ({
  exportWorkRecords: vi.fn(),
  downloadExport: vi.fn(),
}))

const exportMock = vi.mocked(exportWorkRecords)
const downloadMock = vi.mocked(downloadExport)

const titleColumn: RecordListColumn = {
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
  exportable: true,
  sortOrder: 0,
}

const secretColumn: RecordListColumn = {
  ...titleColumn,
  key: 'custom.secret',
  title: '秘密字段',
  source: 'custom',
  fieldCode: 'secret',
  exportable: false,
}

const dictionaryColumn: RecordListColumn = {
  ...titleColumn,
  key: 'custom.priority',
  title: '优先级',
  source: 'custom',
  fieldCode: 'priority',
  fieldType: 'select',
  optionSource: 'dict',
  dictCode: 'record_priority',
}

const meta: RecordListMeta = {
  templates: [],
  columns: [titleColumn, secretColumn],
  exportColumns: [titleColumn, secretColumn],
  dictCodes: [],
  maxExportRows: 5000,
  quickViews: ['all'],
}

const query: ListQueryState = {
  page: 1,
  pageSize: 20,
  quickView: 'all',
  workdayCount: 5,
  templateId: '',
  templateVersionId: '',
  statuses: [],
  ownerId: '',
  creatorId: '',
  keyword: '',
  recordTimeFrom: '',
  recordTimeTo: '',
  sortBy: 'recordTime',
  sortDir: 'desc',
  visibleColumns: [],
}

describe('WorkRecordExportDialog', () => {
  beforeEach(() => {
    exportMock.mockReset()
    downloadMock.mockReset()

    exportMock.mockResolvedValue({
      blob: new Blob(['csv'], { type: 'text/csv;charset=UTF-8' }),
      fileName: 'records.csv',
      rowCount: 1,
    })
  })

  it('does not expose non-exportable columns', async () => {
    const screen = await render(
      <WorkRecordExportDialog
        open
        onOpenChange={vi.fn()}
        query={query}
        meta={meta}
        currentColumns={[titleColumn]}
        total={1}
        selectedIds={[]}
      />
    )

    await expect.element(screen.getByText('标题')).toBeVisible()

    await expect.element(screen.getByText('秘密字段')).not.toBeInTheDocument()
  })

  it('explains how dictionary fields are exported', async () => {
    const screen = await render(
      <WorkRecordExportDialog
        open
        onOpenChange={vi.fn()}
        query={query}
        meta={{
          ...meta,
          columns: [...meta.columns, dictionaryColumn],
          exportColumns: [...meta.exportColumns, dictionaryColumn],
          dictCodes: ['record_priority'],
        }}
        currentColumns={[titleColumn, dictionaryColumn]}
        total={1}
        selectedIds={[]}
      />
    )

    await expect.element(screen.getByText('字典说明')).toBeVisible()
    await expect
      .element(
        screen.getByText(
          '字典字段导出显示名称；历史禁用项标记为“已禁用”；无法识别的值保留原始编码；多选值使用分号分隔。'
        )
      )
      .toBeVisible()
  })

  it('exports confirmed selected columns', async () => {
    const onOpenChange = vi.fn()

    const screen = await render(
      <WorkRecordExportDialog
        open
        onOpenChange={onOpenChange}
        query={query}
        meta={meta}
        currentColumns={[titleColumn]}
        total={1}
        selectedIds={[]}
      />
    )

    await screen.getByRole('checkbox', { name: /我确认导出/ }).click()

    await screen.getByRole('button', { name: '确认导出' }).click()

    expect(exportMock).toHaveBeenCalledWith(query, ['title'], undefined)

    expect(downloadMock).toHaveBeenCalledOnce()
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('defaults to selected scope and exports only checked record ids', async () => {
    const onOpenChange = vi.fn()

    const screen = await render(
      <WorkRecordExportDialog
        open
        onOpenChange={onOpenChange}
        query={query}
        meta={meta}
        currentColumns={[titleColumn]}
        total={100}
        selectedIds={['record-1', 'record-2']}
      />
    )

    await expect
      .element(screen.getByRole('radio', { name: /仅勾选的记录/ }))
      .toBeChecked()

    await screen.getByRole('checkbox', { name: /我确认导出/ }).click()
    await screen.getByRole('button', { name: '确认导出' }).click()

    expect(exportMock).toHaveBeenCalledWith(
      query,
      ['title'],
      ['record-1', 'record-2']
    )
  })

  it('exports the full filtered result when switching back to filtered scope', async () => {
    const screen = await render(
      <WorkRecordExportDialog
        open
        onOpenChange={vi.fn()}
        query={query}
        meta={meta}
        currentColumns={[titleColumn]}
        total={100}
        selectedIds={['record-1']}
      />
    )

    await screen.getByRole('radio', { name: /全部筛选结果/ }).click()

    await screen.getByRole('checkbox', { name: /我确认导出/ }).click()
    await screen.getByRole('button', { name: '确认导出' }).click()

    expect(exportMock).toHaveBeenCalledWith(query, ['title'], undefined)
  })

  it('disables the selected scope when nothing is checked', async () => {
    const screen = await render(
      <WorkRecordExportDialog
        open
        onOpenChange={vi.fn()}
        query={query}
        meta={meta}
        currentColumns={[titleColumn]}
        total={1}
        selectedIds={[]}
      />
    )

    await expect
      .element(screen.getByRole('radio', { name: /仅勾选的记录/ }))
      .toBeDisabled()
  })

  it('blocks selected exports over the configured limit', async () => {
    const screen = await render(
      <WorkRecordExportDialog
        open
        onOpenChange={vi.fn()}
        query={query}
        meta={{ ...meta, maxExportRows: 1 }}
        currentColumns={[titleColumn]}
        total={1}
        selectedIds={['record-1', 'record-2']}
      />
    )

    await expect
      .element(screen.getByRole('button', { name: '确认导出' }))
      .toBeDisabled()

    expect(exportMock).not.toHaveBeenCalled()
  })

  it('blocks exports over the configured limit', async () => {
    const screen = await render(
      <WorkRecordExportDialog
        open
        onOpenChange={vi.fn()}
        query={query}
        meta={{ ...meta, maxExportRows: 10 }}
        currentColumns={[titleColumn]}
        total={11}
        selectedIds={[]}
      />
    )

    await expect
      .element(screen.getByRole('button', { name: '确认导出' }))
      .toBeDisabled()

    expect(exportMock).not.toHaveBeenCalled()
  })
})

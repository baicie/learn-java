import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { downloadExport, exportWorkRecords } from './export-api'
import { WorkRecordExportDialog } from './work-record-export-dialog'
import type {
  ListQueryState,
  RecordListColumn,
  RecordListMeta,
} from './types'

vi.mock('./export-api', () => ({
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

const meta: RecordListMeta = {
  templates: [],
  columns: [titleColumn, secretColumn],
  exportColumns: [titleColumn, secretColumn],
  filterFields: [],
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
  dynamicFilters: [],
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
      />
    )

    await expect.element(screen.getByText('标题')).toBeVisible()

    await expect
      .element(screen.getByText('秘密字段'))
      .not.toBeInTheDocument()
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
      />
    )

    await screen
      .getByRole('checkbox', { name: /我确认导出/ })
      .click()

    await screen
      .getByRole('button', { name: '确认导出' })
      .click()

    expect(exportMock).toHaveBeenCalledWith(query, ['title'])

    expect(downloadMock).toHaveBeenCalledOnce()
    expect(onOpenChange).toHaveBeenCalledWith(false)
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
      />
    )

    await expect
      .element(
        screen.getByRole('button', { name: '确认导出' })
      )
      .toBeDisabled()

    expect(exportMock).not.toHaveBeenCalled()
  })
})

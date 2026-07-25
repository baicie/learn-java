import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import {
  downloadImportTemplate,
  downloadWorkRecordImportTemplate,
} from '@/api/work-records/imports'
import type { RecordListMeta } from './types'
import { WorkRecordImportDialog } from './work-record-import-dialog'

vi.mock('@/api/work-records/imports', () => ({
  importWorkRecords: vi.fn(),
  downloadWorkRecordImportTemplate: vi.fn(),
  downloadImportTemplate: vi.fn(),
}))

const downloadTemplateMock = vi.mocked(downloadWorkRecordImportTemplate)
const saveTemplateMock = vi.mocked(downloadImportTemplate)

const meta: RecordListMeta = {
  templates: [
    {
      id: 'template-1',
      code: 'daily',
      name: '日报',
      status: 'published',
      enabled: true,
      currentVersionId: 'version-3',
    },
  ],
  columns: [],
  exportColumns: [],
  dictCodes: [],
  maxExportRows: 5000,
  quickViews: ['all'],
}

describe('WorkRecordImportDialog', () => {
  beforeEach(() => {
    downloadTemplateMock.mockReset()
    saveTemplateMock.mockReset()
    downloadTemplateMock.mockResolvedValue({
      blob: new Blob(['xlsx']),
      fileName: 'daily-v3.xlsx',
    })
  })

  it('downloads fields from the selected published template version', async () => {
    const screen = await render(
      <WorkRecordImportDialog open onOpenChange={vi.fn()} meta={meta} />
    )
    const downloadButton = screen.getByRole('button', {
      name: '下载导入模板',
    })

    await expect.element(downloadButton).toBeDisabled()

    await screen.getByRole('combobox', { name: '目标工作类型' }).click()
    await screen.getByRole('option', { name: '日报' }).click()
    await downloadButton.click()

    expect(downloadTemplateMock).toHaveBeenCalledWith('template-1', 'version-3')
    expect(saveTemplateMock).toHaveBeenCalledWith({
      blob: expect.any(Blob),
      fileName: 'daily-v3.xlsx',
    })
  })
})

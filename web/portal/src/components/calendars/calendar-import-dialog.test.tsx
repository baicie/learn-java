import { setLanguage } from '@/i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import {
  downloadCalendarImportTemplate,
  importCalendarXlsx,
  saveCalendarImportTemplate,
} from '@/api/calendars'
import { notify } from '@/components/feedback/app-toaster'
import { CalendarImportDialog } from './calendar-import-dialog'

vi.mock('@/api/calendars', () => ({
  downloadCalendarImportTemplate: vi.fn(),
  importCalendarXlsx: vi.fn(),
  saveCalendarImportTemplate: vi.fn(),
}))

vi.mock('@/components/feedback/app-toaster', () => ({
  notify: { success: vi.fn(), error: vi.fn() },
}))

const downloadTemplateMock = vi.mocked(downloadCalendarImportTemplate)
const importCalendarMock = vi.mocked(importCalendarXlsx)
const saveTemplateMock = vi.mocked(saveCalendarImportTemplate)

describe('CalendarImportDialog', () => {
  beforeEach(() => {
    setLanguage('zh-CN')
    vi.clearAllMocks()
    downloadTemplateMock.mockResolvedValue({
      blob: new Blob(['xlsx']),
      fileName: 'work-calendar-holidays-2026-template.xlsx',
    })
    importCalendarMock.mockResolvedValue(1)
  })

  it('downloads the template for the selected calendar year', async () => {
    const screen = await render(
      <CalendarImportDialog
        open
        onOpenChange={vi.fn()}
        calendarId='calendar-2026'
        calendarName='中国大陆 2026 工作日历'
        year={2026}
        onImported={vi.fn()}
      />
    )

    await screen.getByRole('button', { name: '下载 XLSX 模板' }).click()

    expect(downloadTemplateMock).toHaveBeenCalledWith(2026)
    expect(saveTemplateMock).toHaveBeenCalledWith({
      blob: expect.any(Blob),
      fileName: 'work-calendar-holidays-2026-template.xlsx',
    })
    expect(notify.success).toHaveBeenCalledWith('导入模板已下载')
  })

  it('rejects files that are not xlsx workbooks', async () => {
    const screen = await render(
      <CalendarImportDialog
        open
        onOpenChange={vi.fn()}
        calendarId='calendar-2026'
        calendarName='中国大陆 2026 工作日历'
        year={2026}
        onImported={vi.fn()}
      />
    )
    const fileInput = screen.getByLabelText('XLSX 文件')

    await userEvent.upload(fileInput, new File(['csv'], 'holidays.csv'))

    await expect
      .element(screen.getByText('请选择不超过 5 MB 的 .xlsx 文件。'))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '开始导入' }))
      .toBeDisabled()
    expect(importCalendarMock).not.toHaveBeenCalled()
  })

  it('rejects xlsx workbooks larger than 5 MB', async () => {
    const screen = await render(
      <CalendarImportDialog
        open
        onOpenChange={vi.fn()}
        calendarId='calendar-2026'
        calendarName='中国大陆 2026 工作日历'
        year={2026}
        onImported={vi.fn()}
      />
    )
    const oversizedFile = new File(
      [new Uint8Array(5 * 1024 * 1024 + 1)],
      'holidays.xlsx'
    )

    await userEvent.upload(screen.getByLabelText('XLSX 文件'), oversizedFile)

    await expect
      .element(screen.getByText('请选择不超过 5 MB 的 .xlsx 文件。'))
      .toBeVisible()
    await expect
      .element(screen.getByRole('button', { name: '开始导入' }))
      .toBeDisabled()
    expect(importCalendarMock).not.toHaveBeenCalled()
  })

  it('imports a valid workbook, refreshes data, and closes', async () => {
    const onImported = vi.fn().mockResolvedValue(undefined)
    const onOpenChange = vi.fn()
    const screen = await render(
      <CalendarImportDialog
        open
        onOpenChange={onOpenChange}
        calendarId='calendar-2026'
        calendarName='中国大陆 2026 工作日历'
        year={2026}
        onImported={onImported}
      />
    )
    const file = new File(['xlsx'], '2026-holidays.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })

    await userEvent.upload(screen.getByLabelText('XLSX 文件'), file)
    await screen.getByRole('button', { name: '开始导入' }).click()

    expect(importCalendarMock).toHaveBeenCalledWith('calendar-2026', file)
    expect(onImported).toHaveBeenCalledOnce()
    expect(notify.success).toHaveBeenCalledWith('法定节假日导入成功')
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })
})

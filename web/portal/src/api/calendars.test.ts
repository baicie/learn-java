import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api-client'
import {
  downloadCalendarImportTemplate,
  importCalendarXlsx,
  saveCalendarImportTemplate,
} from './calendars'

vi.mock('@/lib/api-client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}))

describe('calendar import api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uploads an xlsx file as multipart form data', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { success: true, data: 2 },
    })
    const file = new File(['xlsx'], '2026-holidays.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })

    const importedRows = await importCalendarXlsx('calendar-2026', file)

    expect(importedRows).toBe(2)
    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/platform/calendars/calendar-2026/days/import',
      expect.any(FormData)
    )
    const form = vi.mocked(apiClient.post).mock.calls[0]?.[1] as FormData
    expect(form.get('file')).toBe(file)
  })

  it('downloads the xlsx template for the requested year', async () => {
    const blob = new Blob(['xlsx'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    vi.mocked(apiClient.get).mockResolvedValue({ data: blob })

    const template = await downloadCalendarImportTemplate(2026)

    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/platform/calendars/import-template',
      { params: { year: 2026 }, responseType: 'blob' }
    )
    expect(template).toEqual({
      blob,
      fileName: 'work-calendar-holidays-2026-template.xlsx',
    })
  })

  it('saves a downloaded template and releases the object url', () => {
    const createObjectUrl = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:calendar-template')
    const revokeObjectUrl = vi
      .spyOn(URL, 'revokeObjectURL')
      .mockImplementation(() => undefined)
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined)

    try {
      saveCalendarImportTemplate({
        blob: new Blob(['xlsx']),
        fileName: 'calendar-template.xlsx',
      })

      expect(click).toHaveBeenCalledOnce()
      expect(revokeObjectUrl).toHaveBeenCalledWith('blob:calendar-template')
    } finally {
      click.mockRestore()
      revokeObjectUrl.mockRestore()
      createObjectUrl.mockRestore()
    }
  })
})

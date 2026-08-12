import { z } from 'zod'
import { apiClient } from '@/lib/api-client'
import { apiResponseSchema } from '@/lib/api-response'

const calendarSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  calendarCode: z.string(),
  calendarName: z.string(),
  regionCode: z.string(),
  timezone: z.string(),
  year: z.number(),
  enabled: z.boolean(),
  sourceType: z.string(),
  description: z.string().nullable().optional(),
  createdBy: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

const calendarDaySchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  calendarId: z.string(),
  calendarDate: z.string(),
  dayOfWeek: z.number(),
  dayType: z.string(),
  workday: z.boolean(),
  holidayCode: z.string().nullable().optional(),
  holidayName: z.string().nullable().optional(),
  sourceType: z.string(),
  remark: z.string().nullable().optional(),
  createdBy: z.string().nullable(),
  createdAt: z.string().nullable(),
  updatedAt: z.string().nullable(),
})

export type Calendar = z.infer<typeof calendarSchema>
export type CalendarDay = z.infer<typeof calendarDaySchema>

const XLSX_CONTENT_TYPE =
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

export type CalendarImportTemplate = {
  blob: Blob
  fileName: string
}

export async function listCalendars(): Promise<Calendar[]> {
  const { data } = await apiClient.get('/api/platform/calendars')
  return apiResponseSchema(z.array(calendarSchema)).parse(data).data
}

export async function listCalendarDays(
  calendarId: string,
  start: string,
  end: string
): Promise<CalendarDay[]> {
  const { data } = await apiClient.get(
    `/api/platform/calendars/${calendarId}/days`,
    {
      params: { start, end },
    }
  )
  return apiResponseSchema(z.array(calendarDaySchema)).parse(data).data
}

export async function updateCalendarDay(
  calendarId: string,
  date: string,
  input: {
    dayType: string
    workday: boolean
    holidayCode?: string
    holidayName?: string
    sourceType?: string
    remark?: string
  }
) {
  const { data } = await apiClient.put(
    `/api/platform/calendars/${calendarId}/days/${date}`,
    input
  )
  return apiResponseSchema(calendarDaySchema).parse(data).data
}

export async function importCalendarXlsx(calendarId: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post(
    `/api/platform/calendars/${calendarId}/days/import`,
    form
  )
  return apiResponseSchema(z.number()).parse(data).data
}

export async function downloadCalendarImportTemplate(
  year: number
): Promise<CalendarImportTemplate> {
  const response = await apiClient.get(
    '/api/platform/calendars/import-template',
    { params: { year }, responseType: 'blob' }
  )
  const fileName = parseCalendarFileName(
    response.headers?.['content-disposition'] ??
      response.headers?.['Content-Disposition'],
    year
  )
  return {
    blob:
      response.data instanceof Blob
        ? response.data
        : new Blob([response.data], { type: XLSX_CONTENT_TYPE }),
    fileName,
  }
}

function parseCalendarFileName(contentDisposition?: string, year?: number) {
  const fallback = `法定节假日-${year ?? new Date().getFullYear()}-导入模板.xlsx`
  if (!contentDisposition) return fallback
  const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition)
  if (utf8Match) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      // fallthrough
    }
  }
  const plainMatch = /filename="?([^";]+)"?/i.exec(contentDisposition)
  if (plainMatch) return plainMatch[1]
  return fallback
}

export function saveCalendarImportTemplate(template: CalendarImportTemplate) {
  const url = URL.createObjectURL(template.blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = template.fileName
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

export async function getDefaultCalendar(year: number): Promise<Calendar> {
  const { data } = await apiClient.get('/api/platform/calendars/default', {
    params: { year },
  })
  return apiResponseSchema(calendarSchema).parse(data).data
}

export async function setDefaultCalendar(
  calendarId: string
): Promise<Calendar> {
  const { data } = await apiClient.put(
    `/api/platform/calendars/${calendarId}/default`
  )
  return apiResponseSchema(calendarSchema).parse(data).data
}

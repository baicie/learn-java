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

export async function listCalendars(): Promise<Calendar[]> {
  const { data } = await apiClient.get('/api/platform/calendars')
  return apiResponseSchema(z.array(calendarSchema)).parse(data).data
}

export async function createCalendar(input: {
  calendarCode: string
  calendarName: string
  regionCode: string
  timezone: string
  year: number
  enabled?: boolean
  sourceType?: string
  description?: string
}) {
  const { data } = await apiClient.post('/api/platform/calendars', input)
  return apiResponseSchema(calendarSchema).parse(data).data
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

export async function importCalendarCsv(calendarId: string, csv: string) {
  const { data } = await apiClient.post(
    `/api/platform/calendars/${calendarId}/days/import`,
    {
      csv,
    }
  )
  return apiResponseSchema(z.number()).parse(data).data
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

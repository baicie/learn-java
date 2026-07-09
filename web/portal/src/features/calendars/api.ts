import { z } from 'zod'
import { workRecordHttp } from '@/features/work-records/api/http'
import { apiResponseSchema } from '@/features/work-records/data/schema'

export const calendarSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  calendarCode: z.string(),
  calendarName: z.string(),
  regionCode: z.string(),
  timezone: z.string(),
  year: z.number(),
  enabled: z.boolean(),
  sourceType: z.string(),
  description: z.string().nullable(),
  createdBy: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const calendarDaySchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  calendarId: z.string(),
  calendarDate: z.string(),
  dayOfWeek: z.number(),
  dayType: z.string(),
  workday: z.boolean(),
  holidayCode: z.string().nullable(),
  holidayName: z.string().nullable(),
  sourceType: z.string(),
  remark: z.string().nullable(),
  createdBy: z.string().nullable(),
  createdAt: z.string().nullable(),
  updatedAt: z.string().nullable(),
})

export type Calendar = z.infer<typeof calendarSchema>
export type CalendarDay = z.infer<typeof calendarDaySchema>

export async function listCalendars(): Promise<Calendar[]> {
  const { data } = await workRecordHttp.get('/api/platform/calendars')
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
  const { data } = await workRecordHttp.post('/api/platform/calendars', input)
  return apiResponseSchema(calendarSchema).parse(data).data
}

export async function listCalendarDays(
  calendarId: string,
  start: string,
  end: string
): Promise<CalendarDay[]> {
  const { data } = await workRecordHttp.get(
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
  const { data } = await workRecordHttp.put(
    `/api/platform/calendars/${calendarId}/days/${date}`,
    input
  )
  return apiResponseSchema(calendarDaySchema).parse(data).data
}

export async function importCalendarCsv(calendarId: string, csv: string) {
  const { data } = await workRecordHttp.post(
    `/api/platform/calendars/${calendarId}/days/import`,
    {
      csv,
    }
  )
  return apiResponseSchema(z.number()).parse(data).data
}

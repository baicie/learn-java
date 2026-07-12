import type { CalendarDay } from './api'

const pad = (value: number) => String(value).padStart(2, '0')

export function formatDayKey(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function buildDayMap(days: CalendarDay[]): Map<string, CalendarDay> {
  const map = new Map<string, CalendarDay>()
  for (const day of days) {
    map.set(day.calendarDate, day)
  }
  return map
}

export function lookupDay(
  dayMap: Map<string, CalendarDay>,
  date: Date
): CalendarDay | undefined {
  return dayMap.get(formatDayKey(date))
}

type DayClassInput = {
  dayData: CalendarDay | undefined
  outside: boolean
  today: boolean
}

export function getDayClassName({
  dayData,
  outside,
  today,
}: DayClassInput): string {
  if (outside) return 'text-muted-foreground opacity-50'
  if (!dayData) return today ? 'ring-1 ring-primary' : ''

  let result = ''
  if (dayData.dayType === 'HOLIDAY' && !dayData.workday) {
    result =
      'bg-red-100 text-red-900 hover:bg-red-200 dark:bg-red-950/40 dark:text-red-200'
  } else if (dayData.dayType === 'ADJUSTED_WORKDAY' && dayData.workday) {
    result =
      'bg-emerald-100 text-emerald-900 hover:bg-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-200'
  } else if (!dayData.workday) {
    result = 'text-muted-foreground'
  }

  if (today) {
    result += ' ring-1 ring-primary'
  }
  return result.trim()
}

export function pickDayKind(dayData: CalendarDay | undefined): {
  label: 'workday' | 'off'
} {
  if (!dayData) return { label: 'workday' }
  return { label: dayData.workday ? 'workday' : 'off' }
}

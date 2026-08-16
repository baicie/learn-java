type DateValue = Date | string | number | null | undefined

export function formatDate(value: DateValue): string {
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return value.replace(/-/g, '/')
  }

  const date = toDate(value)
  return date ? datePart(date) : fallback(value)
}

export function formatDateTime(value: DateValue): string {
  const date = toDate(value)
  if (!date) return fallback(value)

  return `${datePart(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function toCalendarDate(value: string): string {
  const calendarDate = value.slice(0, 10)
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    assertCalendarDate(calendarDate)
    return calendarDate
  }
  if (/^\d{4}-\d{2}-\d{2}T.*[+-]\d{2}:\d{2}$/.test(value)) {
    assertCalendarDate(calendarDate)
    if (Number.isNaN(new Date(value).getTime())) {
      throw new RangeError(`Invalid calendar date: ${value}`)
    }
    return calendarDate
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    throw new RangeError(`Invalid calendar date: ${value}`)
  }
  return localDatePart(date)
}

function assertCalendarDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    throw new RangeError(`Invalid calendar date: ${value}`)
  }
}

export function startOfIsoWeek(value: string): string {
  const date = new Date(`${toCalendarDate(value)}T00:00:00.000Z`)
  if (Number.isNaN(date.getTime())) {
    throw new RangeError(`Invalid calendar date: ${value}`)
  }
  const day = date.getUTCDay() || 7
  date.setUTCDate(date.getUTCDate() - day + 1)
  return date.toISOString().slice(0, 10)
}

export function toLocalOffsetDateTime(date: Date): string {
  if (Number.isNaN(date.getTime())) {
    throw new RangeError('Invalid date')
  }
  const offsetMinutes = -date.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  const absoluteOffset = Math.abs(offsetMinutes)
  const offset = `${sign}${pad(Math.floor(absoluteOffset / 60))}:${pad(absoluteOffset % 60)}`
  return `${localDatePart(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${String(date.getMilliseconds()).padStart(3, '0')}${offset}`
}

function toDate(value: DateValue) {
  if (value === null || value === undefined || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function datePart(date: Date) {
  return `${String(date.getFullYear()).padStart(4, '0')}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

function localDatePart(date: Date) {
  return `${String(date.getFullYear()).padStart(4, '0')}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function fallback(value: DateValue) {
  return typeof value === 'string' && value ? value : '-'
}

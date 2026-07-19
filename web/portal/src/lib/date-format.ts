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

function toDate(value: DateValue) {
  if (value === null || value === undefined || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function datePart(date: Date) {
  return `${String(date.getFullYear()).padStart(4, '0')}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function fallback(value: DateValue) {
  return typeof value === 'string' && value ? value : '-'
}

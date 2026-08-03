import type { ColumnFiltersState } from '@tanstack/react-table'
import type { AlertEvent } from '@/lib/operations/operations'

export type AlertFilter = {
  keyword?: string
  statuses?: string[]
  severities?: string[]
}

export type AlertPagination = {
  page: number
  pageSize: number
}

export function alertFilterValues(filters: ColumnFiltersState, id: string) {
  const value = filters.find((filter) => filter.id === id)?.value
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : []
}

export function filterAlerts(alerts: AlertEvent[], filter: AlertFilter) {
  const keyword = filter.keyword?.trim().toLocaleLowerCase() ?? ''
  const statuses = new Set(filter.statuses ?? [])
  const severities = new Set(filter.severities ?? [])

  return alerts.filter((alert) => {
    const matchesKeyword =
      !keyword ||
      [alert.title, alert.source, alert.severity, alert.status].some((value) =>
        value.toLocaleLowerCase().includes(keyword)
      )
    const matchesStatus = !statuses.size || statuses.has(alert.status)
    const matchesSeverity = !severities.size || severities.has(alert.severity)

    return matchesKeyword && matchesStatus && matchesSeverity
  })
}

export function paginateAlerts(
  alerts: AlertEvent[],
  pagination: AlertPagination
) {
  const pageSize = Math.max(1, pagination.pageSize)
  const total = alerts.length
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const page = Math.min(Math.max(1, pagination.page), pageCount)
  const start = (page - 1) * pageSize

  return {
    items: alerts.slice(start, start + pageSize),
    total,
    pageCount,
  }
}

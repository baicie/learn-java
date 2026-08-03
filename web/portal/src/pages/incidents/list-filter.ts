import type { Incident } from '@/lib/operations/operations'

export type IncidentListFilters = {
  keyword?: string
  status?: string
  severity?: string
  source?: string
}

export type IncidentPagination = {
  page: number
  pageSize: number
}

export function filterIncidents(
  incidents: Incident[],
  filters: IncidentListFilters
) {
  const keyword = filters.keyword?.trim().toLocaleLowerCase() ?? ''

  return incidents.filter((incident) => {
    const matchesKeyword =
      !keyword ||
      [incident.title, incident.summary, incident.id]
        .filter((value): value is string => Boolean(value))
        .some((value) => value.toLocaleLowerCase().includes(keyword))

    return (
      matchesKeyword &&
      (!filters.status || incident.status === filters.status) &&
      (!filters.severity || incident.severity === filters.severity) &&
      (!filters.source || incident.source === filters.source)
    )
  })
}

export function paginateIncidents(
  incidents: Incident[],
  { page, pageSize }: IncidentPagination
) {
  const total = incidents.length
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(Math.max(1, page), pageCount)
  const start = (safePage - 1) * pageSize

  return {
    items: incidents.slice(start, start + pageSize),
    total,
    pageCount,
  }
}

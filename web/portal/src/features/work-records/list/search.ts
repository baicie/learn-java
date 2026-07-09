import type { DynamicFilter, ListQueryState } from './types'

export const DEFAULT_LIST_QUERY: ListQueryState = {
  page: 1,
  pageSize: 20,
  quickView: 'all',
  templateId: '',
  statuses: [],
  ownerId: '',
  creatorId: '',
  keyword: '',
  recordTimeFrom: '',
  recordTimeTo: '',
  sortBy: 'recordTime',
  sortDir: 'desc',
  dynamicFilters: [],
  visibleColumns: [],
}

export function parseListSearch(search: URLSearchParams): ListQueryState {
  return {
    ...DEFAULT_LIST_QUERY,
    page: Number(search.get('page') ?? 1),
    pageSize: Number(search.get('pageSize') ?? 20),
    quickView: search.get('quickView') ?? 'all',
    templateId: search.get('templateId') ?? '',
    statuses: search.getAll('status'),
    ownerId: search.get('ownerId') ?? '',
    creatorId: search.get('creatorId') ?? '',
    keyword: search.get('keyword') ?? '',
    recordTimeFrom: search.get('recordTimeFrom') ?? '',
    recordTimeTo: search.get('recordTimeTo') ?? '',
    sortBy: search.get('sortBy') ?? 'recordTime',
    sortDir: search.get('sortDir') === 'asc' ? 'asc' : 'desc',
    dynamicFilters: parseDynamicFilters(search.get('dynamicFilters')),
    visibleColumns: search.getAll('column'),
  }
}

export function stringifyListSearch(state: ListQueryState) {
  const search = new URLSearchParams()
  search.set('page', String(state.page))
  search.set('pageSize', String(state.pageSize))
  search.set('quickView', state.quickView)

  set(search, 'templateId', state.templateId)
  set(search, 'ownerId', state.ownerId)
  set(search, 'creatorId', state.creatorId)
  set(search, 'keyword', state.keyword)
  set(search, 'recordTimeFrom', state.recordTimeFrom)
  set(search, 'recordTimeTo', state.recordTimeTo)
  set(search, 'sortBy', state.sortBy)
  set(search, 'sortDir', state.sortDir)

  for (const status of state.statuses) {
    search.append('status', status)
  }
  for (const column of state.visibleColumns) {
    search.append('column', column)
  }
  if (state.dynamicFilters.length) {
    search.set('dynamicFilters', JSON.stringify(state.dynamicFilters))
  }

  return search
}

function parseDynamicFilters(raw: string | null): DynamicFilter[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function set(search: URLSearchParams, key: string, value: string) {
  if (value && value.trim()) search.set(key, value)
}
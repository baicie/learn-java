import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchRecordList, fetchRecordListMeta } from './api'
import { DEFAULT_LIST_QUERY } from './search'
import type { ListQueryState } from './types'

export function useWorkRecordList(initial?: Partial<ListQueryState>) {
  const [query, setQuery] = useState<ListQueryState>({
    ...DEFAULT_LIST_QUERY,
    ...initial,
  })

  const metaQuery = useQuery({
    queryKey: ['work-record-list-meta'],
    queryFn: fetchRecordListMeta,
  })

  const effectiveColumns = useMemo(() => {
    const columns = metaQuery.data?.columns ?? []
    if (query.visibleColumns.length) {
      return columns.filter((column) => query.visibleColumns.includes(column.key))
    }
    return columns.filter((column) => column.visibleByDefault)
  }, [metaQuery.data?.columns, query.visibleColumns])

  const listQuery = useQuery({
    queryKey: ['work-record-list', query],
    queryFn: () => fetchRecordList(query),
  })

  const patchQuery = (patch: Partial<ListQueryState>) => {
    setQuery((current) => ({
      ...current,
      ...patch,
      page: patch.page ?? 1,
    }))
  }

  return {
    query,
    setQuery,
    patchQuery,
    meta: metaQuery.data,
    records: listQuery.data?.items ?? [],
    total: listQuery.data?.total ?? 0,
    effectiveColumns,
    loading: metaQuery.isLoading || listQuery.isLoading,
    error: metaQuery.error ?? listQuery.error,
    refetch: listQuery.refetch,
  }
}
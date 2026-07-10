import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listDictItems } from '@/features/dictionaries/api'
import { fetchRecordList, fetchRecordListMeta } from './api'
import type { DictOptionMap, ListQueryState } from './types'

export function useWorkRecordList(
  query: ListQueryState,
  onQueryChange: (next: ListQueryState) => void
) {
  const metaQuery = useQuery({
    queryKey: ['work-record-list-meta', query.templateId],
    queryFn: () => fetchRecordListMeta(query.templateId || undefined),
  })

  const listQuery = useQuery({
    queryKey: ['work-record-list', query],
    queryFn: () => fetchRecordList(query),
  })

  const dictQuery = useQuery({
    queryKey: ['work-record-list-dicts', metaQuery.data?.dictCodes ?? []],
    enabled: Boolean(metaQuery.data?.dictCodes.length),
    queryFn: async (): Promise<DictOptionMap> => {
      const entries = await Promise.all(
        (metaQuery.data?.dictCodes ?? []).map(async (dictCode) => {
          const items = await listDictItems(dictCode, true)
          return [
            dictCode,
            items.map((item) => ({
              value: item.itemValue,
              label: item.itemLabel,
              enabled: item.enabled,
            })),
          ] as const
        })
      )

      return Object.fromEntries(entries)
    },
  })

  const effectiveColumns = useMemo(() => {
    const columns = metaQuery.data?.columns ?? []

    if (query.visibleColumns.length) {
      return columns.filter((column) =>
        query.visibleColumns.includes(column.key)
      )
    }

    return columns.filter((column) => column.visibleByDefault)
  }, [metaQuery.data?.columns, query.visibleColumns])

  const patchQuery = (patch: Partial<ListQueryState>) => {
    const next: ListQueryState = {
      ...query,
      ...patch,
      page: patch.page !== undefined ? patch.page : 1,
    }

    if (
      patch.templateId !== undefined &&
      patch.templateId !== query.templateId
    ) {
      next.dynamicFilters = []
    }

    onQueryChange(next)
  }

  return {
    query,
    patchQuery,
    meta: metaQuery.data,
    records: listQuery.data?.items ?? [],
    total: listQuery.data?.total ?? 0,
    dictOptions: dictQuery.data ?? {},
    effectiveColumns,
    loading: metaQuery.isLoading || listQuery.isLoading || dictQuery.isLoading,
    error: metaQuery.error ?? listQuery.error ?? dictQuery.error,
  }
}

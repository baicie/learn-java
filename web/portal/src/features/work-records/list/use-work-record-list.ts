import { useEffect, useMemo, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { notify } from '@/components/feedback/app-toaster'
import { useDictionaryOptions } from '@/features/dictionaries/dictionary-query'
import {
  fetchRecordList,
  fetchRecordListMeta,
  fetchWorkdaySummary,
} from './api'
import type { ListQueryState } from './types'

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
    placeholderData: (previous) => previous,
  })

  const dictionaryQuery = useDictionaryOptions(
    metaQuery.data?.dictCodes ?? [],
    true
  )

  const notifiedDictError = useRef<unknown>(null)

  useEffect(() => {
    if (
      dictionaryQuery.error &&
      dictionaryQuery.error !== notifiedDictError.current
    ) {
      notifiedDictError.current = dictionaryQuery.error
      notify.error(
        dictionaryQuery.error,
        '字典标签加载失败，当前暂时显示原始值'
      )
    }
  }, [dictionaryQuery.error])

  const workdaySummaryQuery = useQuery({
    queryKey: ['work-record-workday-summary', 'current'],
    queryFn: () => fetchWorkdaySummary(),
    enabled: query.quickView === 'this_work_month',
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
      next.visibleColumns = []
    }

    onQueryChange(next)
  }

  return {
    query,
    patchQuery,

    meta: metaQuery.data,
    records: listQuery.data?.items ?? [],
    total: listQuery.data?.total ?? 0,

    dictOptions: dictionaryQuery.options,
    effectiveColumns,

    initialLoading: metaQuery.isLoading && !metaQuery.data,

    pageError: metaQuery.error,

    tableLoading: listQuery.isLoading && !listQuery.data,

    tableRefreshing: listQuery.isFetching && Boolean(listQuery.data),

    tableError: listQuery.error,

    retryPage: () => metaQuery.refetch(),
    retryTable: () => listQuery.refetch(),

    workdaySummary: workdaySummaryQuery.data,
    workdaySummaryLoading: workdaySummaryQuery.isLoading,
    workdaySummaryError: workdaySummaryQuery.error,
  }
}

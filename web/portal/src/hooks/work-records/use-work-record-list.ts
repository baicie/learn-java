import { useEffect, useMemo, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  fetchRecordList,
  fetchRecordListMeta,
  fetchRecordUserNames,
  fetchWorkdaySummary,
} from '@/api/work-records/list'
import { useDictionaryOptions } from '@/hooks/dictionaries/dictionary-query'
import { notify } from '@/components/feedback/app-toaster'
import type { ListQueryState } from '@/components/work-records/list/types'

export function useWorkRecordList(
  query: ListQueryState,
  onQueryChange: (next: ListQueryState) => void
) {
  const { t } = useTranslation()

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

  const userIds = useMemo(
    () =>
      Array.from(
        new Set(
          (listQuery.data?.items ?? []).flatMap((record) =>
            [record.creatorId, record.ownerId].filter(
              (value): value is string => Boolean(value)
            )
          )
        )
      ),
    [listQuery.data?.items]
  )
  const userNamesQuery = useQuery({
    queryKey: ['work-record-user-names', userIds],
    queryFn: () => fetchRecordUserNames(userIds),
    enabled: userIds.length > 0,
  })

  const notifiedDictError = useRef<unknown>(null)
  const notifiedRefreshError = useRef<unknown>(null)

  useEffect(() => {
    if (
      dictionaryQuery.error &&
      dictionaryQuery.error !== notifiedDictError.current
    ) {
      notifiedDictError.current = dictionaryQuery.error
      notify.error(dictionaryQuery.error, t('workRecords.list.dictLoadFailed'))
    }
  }, [dictionaryQuery.error, t])

  const hasListData = listQuery.data !== undefined
  const tableBlockingError = hasListData ? null : listQuery.error
  const tableRefreshError = hasListData ? listQuery.error : null

  useEffect(() => {
    if (!tableRefreshError) {
      notifiedRefreshError.current = null
      return
    }
    if (tableRefreshError === notifiedRefreshError.current) return

    notifiedRefreshError.current = tableRefreshError
    notify.error(tableRefreshError, t('workRecords.list.refreshFailedHint'))
  }, [tableRefreshError, t])

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
    userNames: userNamesQuery.data ?? {},
    effectiveColumns,

    initialLoading: metaQuery.isLoading && !metaQuery.data,

    pageError: metaQuery.error,

    tableLoading: listQuery.isLoading && !listQuery.data,

    tableRefreshing: listQuery.isFetching && Boolean(listQuery.data),

    tableError: tableBlockingError,

    tableRefreshError,

    retryPage: () => metaQuery.refetch(),
    retryTable: () => listQuery.refetch(),

    workdaySummary: workdaySummaryQuery.data,
    workdaySummaryLoading: workdaySummaryQuery.isLoading,
    workdaySummaryError: workdaySummaryQuery.error,
  }
}

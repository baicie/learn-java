import { useQuery } from '@tanstack/react-query'
import { getRouteApi } from '@tanstack/react-router'
import { useTranslation as _useTranslation } from 'react-i18next'
import { Skeleton } from '@/components/ui/skeleton'
import { listWorkRecords, getListMetadata } from './api/work-record-api'
import { RecordsTable } from './components/records-table'
import { WorkRecordsLayout } from './components/work-records-layout'

const route = getRouteApi('/_authenticated/work-records/')

export function WorkRecords() {
  const search = route.useSearch()
  const navigate = route.useNavigate()

  const records = useQuery({
    queryKey: ['work-records', 'list', search],
    queryFn: () =>
      listWorkRecords({
        page: search.page,
        pageSize: search.pageSize,
        templateId: search.templateId,
        status: search.status,
        keyword: search.keyword,
        recordTimeFrom: search.recordTimeFrom,
        recordTimeTo: search.recordTimeTo,
        filters: search.filters,
      }),
  })

  const metadata = useQuery({
    queryKey: ['work-records', 'metadata', search.templateId],
    queryFn: () => getListMetadata(search.templateId),
  })

  const handleQueryChange = (newSearch: Record<string, unknown>) => {
    navigate({ search: newSearch })
  }

  return (
    <WorkRecordsLayout
      titleKey='workRecords.list.title'
      descriptionKey='workRecords.list.description'
    >
      {records.isLoading ? (
        <Skeleton className='h-64 w-full' />
      ) : (
        <RecordsTable
          data={records.data?.items ?? []}
          total={records.data?.total ?? 0}
          metadata={metadata.data}
          search={search}
          navigate={navigate}
          onQueryChange={handleQueryChange}
        />
      )}
    </WorkRecordsLayout>
  )
}

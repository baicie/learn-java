import { Link, getRouteApi } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ExportRecordsButton } from './components/export-records-dialog'
import { RecordsTable } from './components/records-table'
import { WorkRecordsLayout } from './components/work-records-layout'
import { useRecords } from './hooks/use-records'

const route = getRouteApi('/_authenticated/work-records/')

export function WorkRecords() {
  const { t } = useTranslation()
  const search = route.useSearch()
  const navigate = route.useNavigate()
  const records = useRecords({
    page: search.page,
    pageSize: search.pageSize,
    status: search.status,
  })

  return (
    <WorkRecordsLayout
      titleKey='workRecords.list.title'
      descriptionKey='workRecords.list.description'
      actions={
        <div className='flex flex-wrap gap-2'>
          <ExportRecordsButton status={search.status} />
          <Button asChild>
            <Link to='/work-records/new'>
              <Plus className='size-4' />
              {t('common.create')}
            </Link>
          </Button>
        </div>
      }
    >
      {records.isLoading ? (
        <Skeleton className='h-64 w-full' />
      ) : (
        <RecordsTable
          data={records.data?.items ?? []}
          search={search}
          navigate={navigate}
        />
      )}
    </WorkRecordsLayout>
  )
}

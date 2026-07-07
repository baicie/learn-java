import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from '@tanstack/react-router'
import { Pencil } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { getWorkRecord } from './api/work-record-api'
import { RecordReadonlyView } from './components/record-readonly-view'
import { WorkRecordsLayout } from './components/work-records-layout'

export function WorkRecordDetail() {
  const { recordId } = useParams({
    from: '/_authenticated/work-records/$recordId',
  })
  const record = useQuery({
    queryKey: ['work-record', recordId],
    queryFn: () => getWorkRecord(recordId),
  })

  return (
    <WorkRecordsLayout
      titleKey='workRecords.detail.title'
      actions={
        <Button asChild variant='outline'>
          <Link to='/work-records/$recordId/edit' params={{ recordId }}>
            <Pencil className='size-4' />
            编辑
          </Link>
        </Button>
      }
    >
      {record.isLoading || !record.data ? (
        <Skeleton className='h-64 w-full' />
      ) : (
        <RecordReadonlyView record={record.data} />
      )}
    </WorkRecordsLayout>
  )
}

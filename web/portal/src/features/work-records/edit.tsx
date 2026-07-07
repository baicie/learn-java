import { useQuery } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { Skeleton } from '@/components/ui/skeleton'
import { getWorkRecord } from './api/work-record-api'
import { RecordForm } from './components/record-form'
import { WorkRecordsLayout } from './components/work-records-layout'

export function EditWorkRecord() {
  const { recordId } = useParams({
    from: '/_authenticated/work-records/$recordId/edit',
  })
  const record = useQuery({
    queryKey: ['work-record', recordId],
    queryFn: () => getWorkRecord(recordId),
  })

  return (
    <WorkRecordsLayout titleKey='workRecords.edit.title'>
      {record.isLoading || !record.data ? (
        <Skeleton className='h-64 w-full' />
      ) : (
        <RecordForm record={record.data} />
      )}
    </WorkRecordsLayout>
  )
}

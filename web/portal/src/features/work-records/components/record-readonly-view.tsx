import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { type WorkRecord } from '../data/schema'

type RecordReadonlyViewProps = {
  record: WorkRecord
}

export function RecordReadonlyView({ record }: RecordReadonlyViewProps) {
  return (
    <div className='grid max-w-4xl gap-4'>
      <div className='grid gap-3 rounded-md border p-4'>
        <div className='flex flex-wrap items-center justify-between gap-2'>
          <h3 className='text-lg font-semibold'>{record.title}</h3>
          <Badge variant='outline'>{record.status}</Badge>
        </div>
        <Separator />
        <dl className='grid gap-3 text-sm @md/content:grid-cols-2'>
          <div>
            <dt className='text-muted-foreground'>模板 ID</dt>
            <dd className='font-mono'>{record.templateId}</dd>
          </div>
          <div>
            <dt className='text-muted-foreground'>负责人</dt>
            <dd>{record.ownerId ?? '-'}</dd>
          </div>
          <div>
            <dt className='text-muted-foreground'>创建人</dt>
            <dd>{record.creatorId}</dd>
          </div>
          <div>
            <dt className='text-muted-foreground'>记录时间</dt>
            <dd>{new Date(record.recordTime).toLocaleString()}</dd>
          </div>
        </dl>
      </div>
      <pre className='overflow-auto rounded-md border bg-muted/40 p-4 text-sm'>
        {JSON.stringify(JSON.parse(record.customDataJson || '{}'), null, 2)}
      </pre>
    </div>
  )
}

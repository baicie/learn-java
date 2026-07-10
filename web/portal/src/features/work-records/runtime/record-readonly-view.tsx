import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { DynamicFieldControl } from './dynamic-field-control'
import { RecordHistoryCard } from './record-history-card'
import { statusLabel } from './schema'
import type {
  AuditEvent,
  RuntimeDictOptions,
  WorkRecord,
  WorkRecordField,
  WorkRecordTemplate,
} from './types'

type RecordReadonlyViewProps = {
  record: WorkRecord
  template?: WorkRecordTemplate
  fields: WorkRecordField[]
  dictOptions: RuntimeDictOptions
  customData: Record<string, unknown>
  canEdit?: boolean
  history?: AuditEvent[]
  historyLoading?: boolean
  historyError?: Error | null
  onBack: () => void
  onEdit: () => void
}

export function RecordReadonlyView({
  record,
  template,
  fields,
  dictOptions,
  customData,
  canEdit = true,
  history = [],
  historyLoading = false,
  historyError = null,
  onBack,
  onEdit,
}: RecordReadonlyViewProps) {
  const sortedFields = fields.slice().sort((a, b) => a.sortOrder - b.sortOrder)

  return (
    <main className='grid gap-4 p-6 xl:grid-cols-[minmax(0,1fr)_320px]'>
      <section className='grid gap-4'>
        <Card>
          <CardHeader>
            <CardTitle>{record.title}</CardTitle>
          </CardHeader>
          <CardContent className='grid gap-3 text-sm md:grid-cols-2'>
            <Info label='模板' value={template?.name ?? record.templateId} />
            <Info label='模板版本' value={record.templateVersionId} />
            <Info label='状态' value={statusLabel(record.status)} />
            <Info label='负责人' value={record.ownerId ?? '-'} />
            <Info label='创建人' value={record.creatorId} />
            <Info label='记录时间' value={formatDateTime(record.recordTime)} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>动态字段</CardTitle>
          </CardHeader>
          <CardContent className='grid gap-4'>
            {sortedFields.length === 0 ? (
              <div className='text-sm text-muted-foreground'>暂无动态字段</div>
            ) : null}

            {sortedFields.map((field) => (
              <label key={field.id} className='grid gap-1 text-sm'>
                <span className='font-medium'>
                  {field.fieldName}
                  {!field.enabled ? (
                    <span className='ml-2 rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground'>
                      已禁用字段
                    </span>
                  ) : null}
                </span>
                <DynamicFieldControl
                  field={field}
                  value={customData[field.fieldCode]}
                  dictOptions={dictOptions}
                  readonly
                  onChange={() => undefined}
                />
              </label>
            ))}
          </CardContent>
        </Card>

        <RecordHistoryCard
          events={history}
          loading={historyLoading}
          error={historyError}
        />
      </section>

      <aside className='grid content-start gap-3'>
        <Card>
          <CardHeader>
            <CardTitle>操作</CardTitle>
          </CardHeader>
          <CardContent className='grid gap-2'>
            {canEdit ? (
              <Button type='button' onClick={onEdit}>
                编辑
              </Button>
            ) : null}
            <Button type='button' variant='outline' onClick={onBack}>
              返回
            </Button>
          </CardContent>
        </Card>
      </aside>
    </main>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className='rounded-md border p-3'>
      <div className='text-xs text-muted-foreground'>{label}</div>
      <div className='mt-1 break-all'>{value}</div>
    </div>
  )
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}